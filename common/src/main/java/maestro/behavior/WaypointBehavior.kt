package maestro.behavior

import maestro.Agent
import maestro.api.cache.IWaypoint
import maestro.api.cache.Waypoint
import maestro.api.event.events.BlockInteractEvent
import maestro.api.utils.BetterBlockPos
import maestro.api.utils.MaestroLogger
import maestro.utils.BlockStateInterface
import maestro.utils.chat.ChatMessageBuilder.Companion.info
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.properties.BedPart
import org.slf4j.Logger

/**
 * Tracks and creates waypoints for significant locations.
 *
 * Automatically creates waypoints for:
 * - Bed locations (when used)
 * - Death locations
 */
class WaypointBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    override fun onBlockInteract(event: BlockInteractEvent) {
        if (!Agent.settings().doBedWaypoints.value) return
        if (event.type != BlockInteractEvent.Type.USE) return

        var pos = BetterBlockPos.from(event.pos)
        val state = BlockStateInterface.get(ctx, pos)

        if (state.getBlock() is BedBlock) {
            // Normalize to bed head position
            if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                pos = pos.relative(state.getValue(BedBlock.FACING))
            }

            val waypoints =
                maestro
                    .getWorldProvider()
                    .getCurrentWorld()
                    .getWaypoints()
                    .getByTag(IWaypoint.Tag.BED)

            val exists = waypoints.any { it.getLocation() == pos }

            if (!exists) {
                maestro
                    .getWorldProvider()
                    .getCurrentWorld()
                    .getWaypoints()
                    .addWaypoint(Waypoint("bed", IWaypoint.Tag.BED, pos))
            }
        }
    }

    override fun onPlayerDeath() {
        if (!Agent.settings().doDeathWaypoints.value) return

        val deathWaypoint = Waypoint("death", IWaypoint.Tag.DEATH, ctx.playerFeet())
        maestro
            .getWorldProvider()
            .getCurrentWorld()
            .getWaypoints()
            .addWaypoint(deathWaypoint)

        val pos = ctx.playerFeet()
        info(log, "waypoint")
            .message("Death waypoint saved")
            .key("position", pos)
            .withHover("Click to teleport to death location")
            .withClick("/maestro goto ${pos.getX()} ${pos.getY()} ${pos.getZ()}")
            .send()
    }

    companion object {
        private val log: Logger = MaestroLogger.get("waypoint")
    }
}
