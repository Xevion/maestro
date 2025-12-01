package maestro.api.event.events

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4f

/** Event fired during world rendering */
class RenderEvent(
    /** The current render partial ticks */
    @JvmField val partialTicks: Float,
    @JvmField val modelViewStack: PoseStack,
    @JvmField val projectionMatrix: Matrix4f,
)
