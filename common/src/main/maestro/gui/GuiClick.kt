
package maestro.gui

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import maestro.Agent
import maestro.api.AgentAPI
import maestro.api.pathing.goals.GoalBlock
import maestro.api.utils.Helper
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.gui.chat.ChatMessage
import maestro.rendering.IRenderer
import maestro.rendering.PathRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f
import org.slf4j.Logger
import java.awt.Color

class GuiClick :
    Screen(Component.literal("CLICK")),
    Helper {
    private var projectionViewMatrix: Matrix4f? = null
    private var clickStart: BlockPos? = null
    private var currentMouseOver: BlockPos? = null

    override fun isPauseScreen(): Boolean = false

    override fun render(
        stack: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val mc = Minecraft.getInstance()
        val window = mc.window

        var mx = mc.mouseHandler.xpos()
        var my = mc.mouseHandler.ypos()

        my = window.screenHeight - my
        my *= window.height / window.screenHeight.toDouble()
        mx *= window.width / window.screenWidth.toDouble()

        val near = toWorld(mx, my, 0.0)
        val far = toWorld(mx, my, 1.0) // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            val viewerPos = Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ())
            val player =
                AgentAPI
                    .getProvider()
                    .primaryAgent
                    .playerContext
                    .player()

            val result =
                player.level().clip(
                    ClipContext(
                        near.add(viewerPos),
                        far.add(viewerPos),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player,
                    ),
                )

            if (result != null && result.type == HitResult.Type.BLOCK) {
                currentMouseOver = result.blockPos
            }
        }
    }

    override fun mouseReleased(
        mouseX: Double,
        mouseY: Double,
        mouseButton: Int,
    ): Boolean {
        currentMouseOver?.let { mouseOver ->
            val agent = AgentAPI.getProvider().primaryAgent

            when (mouseButton) {
                0 -> {
                    val start = clickStart
                    if (start != null && start != mouseOver) {
                        agent.selectionManager.removeAllSelections()

                        val from = PackedBlockPos(start)
                        val to = PackedBlockPos(mouseOver)

                        agent.selectionManager.addSelection(from, to)

                        ChatMessage
                            .info(log, "event")
                            .message("Selection made")
                            .key("from", from)
                            .key("to", to)
                            .withHover("Click to select region")
                            .withClick(
                                "/maestro sel ${from.x} ${from.y} ${from.z} ${to.x} ${to.y} ${to.z}",
                            ).send()

                        clickStart = null
                    } else {
                        agent.customGoalTask.setGoalAndPath(GoalBlock(mouseOver))
                    }
                }

                1 -> {
                    agent.customGoalTask.setGoalAndPath(GoalBlock(mouseOver.above()))
                }
            }
        }

        clickStart = null
        return super.mouseReleased(mouseX, mouseY, mouseButton)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        mouseButton: Int,
    ): Boolean {
        clickStart = currentMouseOver
        return super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    fun onRender(
        modelViewStack: PoseStack,
        projectionMatrix: Matrix4f,
    ) {
        this.projectionViewMatrix =
            Matrix4f(projectionMatrix).apply {
                mul(modelViewStack.last().pose())
                invert()
            }

        currentMouseOver?.let { mouseOver ->
            val mc = Minecraft.getInstance()
            val e = mc.cameraEntity

            // drawSingleSelectionBox WHEN?
            PathRenderer.drawManySelectionBoxes(
                modelViewStack,
                e,
                listOf(mouseOver),
                Color.CYAN,
            )

            val start = clickStart
            if (start != null && start != mouseOver) {
                val bufferBuilder: BufferBuilder =
                    IRenderer.startLines(
                        Color.RED,
                        Agent.settings().pathRenderLineWidthPixels.value,
                        true,
                    )

                val a = PackedBlockPos(mouseOver)
                val b = PackedBlockPos(start)

                IRenderer.emitAABB(
                    bufferBuilder,
                    modelViewStack,
                    AABB(
                        minOf(a.x, b.x).toDouble(),
                        minOf(a.y, b.y).toDouble(),
                        minOf(a.z, b.z).toDouble(),
                        maxOf(a.x, b.x).toDouble() + 1,
                        maxOf(a.y, b.y).toDouble() + 1,
                        maxOf(a.z, b.z).toDouble() + 1,
                    ),
                )

                IRenderer.endLines(bufferBuilder, true)
            }
        }
    }

    private fun toWorld(
        x: Double,
        y: Double,
        z: Double,
    ): Vec3? {
        val matrix = projectionViewMatrix ?: return null
        val mc = Minecraft.getInstance()

        val normalizedX = x / mc.window.width
        val normalizedY = y / mc.window.height
        val ndcX = normalizedX * 2 - 1
        val ndcY = normalizedY * 2 - 1

        val pos = Vector4f(ndcX.toFloat(), ndcY.toFloat(), z.toFloat(), 1.0f)
        matrix.transform(pos)

        if (pos.w() == 0f) {
            return null
        }

        pos.mul(1 / pos.w())
        return Vec3(pos.x().toDouble(), pos.y().toDouble(), pos.z().toDouble())
    }

    companion object {
        private val log: Logger = Loggers.get("event")
    }
}
