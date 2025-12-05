package maestro.behavior

import maestro.Agent
import maestro.api.cache.Waypoint
import maestro.api.utils.Loggers
import maestro.gui.chat.ChatMessage.Companion.info
import org.slf4j.Logger

/**
 * Tracks and creates waypoints for significant locations.
 *
 * Automatically creates waypoints for:
 * - Death locations
 */
class WaypointBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    override fun onPlayerDeath() {
        if (!Agent.settings().doDeathWaypoints.value) return

        val world = maestro.worldProvider.getCurrentWorld() ?: return
        val deathWaypoint = Waypoint("death", Waypoint.Tag.DEATH, ctx.playerFeet())
        world.getWaypoints().addWaypoint(deathWaypoint)

        val pos = ctx.playerFeet()
        info(log, "waypoint")
            .message("Death waypoint saved")
            .key("position", pos)
            .withHover("Click to teleport to death location")
            .withClick("/maestro goto ${pos.x} ${pos.y} ${pos.z}")
            .send()
    }

    companion object {
        private val log: Logger = Loggers.Waypoint.get()
    }
}
