package maestro.pathing.movement

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * Describes desired player movement for a single tick.
 *
 * Intent-based movement separates "what to do" from "how to do it":
 * - Movements emit intents describing desired behavior
 * - InputOverrideHandler translates intents to actual Minecraft inputs
 *
 * This abstraction enables:
 * - Cleaner movement implementations (declarative vs imperative)
 * - Centralized input translation logic
 * - Easier testing and debugging
 */
sealed class MovementIntent {
    /**
     * Move toward a target position on the XZ plane.
     *
     * The executor calculates forward/strafe inputs to reach the target.
     * Y-axis navigation is handled by higher-level pathfinding via the jump flag.
     *
     * @param target XZ coordinates to move toward
     * @param speed Movement speed modifier
     * @param jump Whether to hold jump (for ascending, swimming up, etc.)
     * @param startPos Starting position for drift correction (optional)
     */
    data class Toward(
        val target: Vec2,
        val speed: MovementSpeed = MovementSpeed.WALK,
        val jump: Boolean = false,
        val startPos: Vec2? = null,
    ) : MovementIntent()

    /**
     * Direct control of movement inputs.
     *
     * Used when precise input control is needed (combat strafing, parkour timing, etc.)
     * instead of target-based navigation.
     *
     * @param forward Forward/backward input
     * @param strafe Left/right input
     * @param speed Movement speed modifier
     * @param jump Whether to hold jump
     */
    data class Direct(
        val forward: ForwardInput = ForwardInput.NONE,
        val strafe: StrafeInput = StrafeInput.NONE,
        val speed: MovementSpeed = MovementSpeed.WALK,
        val jump: Boolean = false,
    ) : MovementIntent()

    /**
     * Stop all movement.
     *
     * Clears all movement keys (WASD, jump, sprint, sneak).
     */
    object Stop : MovementIntent()
}

/** Forward/backward movement input (binary - matches Minecraft's key-based control). */
enum class ForwardInput {
    FORWARD,
    BACKWARD,
    NONE,
}

/** Left/right strafe input (binary - matches Minecraft's key-based control). */
enum class StrafeInput {
    LEFT,
    RIGHT,
    NONE,
}

/** Movement speed modifier. */
enum class MovementSpeed {
    SPRINT,
    WALK,
    SNEAK,
}

/**
 * Describes desired camera rotation for a single tick.
 *
 * Separates look control from movement control, enabling:
 * - Mining while stationary (stop movement, look at block)
 * - Combat strafing (move in one direction, look at enemy)
 * - Parkour precision (move toward landing, look at target for trajectory)
 */
sealed class LookIntent {
    /**
     * Look at the center of a block.
     *
     * Used for block breaking, placement, and precise targeting.
     */
    data class Block(
        val pos: BlockPos,
    ) : LookIntent()

    /**
     * Look at an entity's eye position.
     *
     * Used for combat targeting and entity following.
     */
    data class Entity(
        val entity: net.minecraft.world.entity.Entity,
    ) : LookIntent()

    /**
     * Look at specific yaw/pitch angles.
     *
     * Used for arbitrary rotation control.
     */
    data class Direction(
        val yaw: Float,
        val pitch: Float,
    ) : LookIntent()

    /**
     * Look at an arbitrary 3D point.
     *
     * Used for looking at positions that aren't block-aligned.
     */
    data class Point(
        val vec: Vec3,
    ) : LookIntent()

    /**
     * No rotation change - maintain current look direction.
     */
    object None : LookIntent()
}

/**
 * Unified intent containing all player inputs for a single tick.
 *
 * Separates "what to do" from "how to do it" for movement, look, and click actions.
 * This enables:
 * - Single computation point per movement (computeIntent)
 * - Delta-based input updates (only change what's different)
 * - Clean separation of decision logic from input execution
 *
 * @param movement Movement intent for this tick
 * @param look Look intent for this tick
 * @param click Click intent for this tick (defaults to None)
 */
data class Intent(
    val movement: MovementIntent,
    val look: LookIntent,
    val click: ClickIntent = ClickIntent.None,
)

/**
 * Describes desired click/interaction for a single tick.
 *
 * Abstract like MovementIntent.Stop - describes the action, not the target.
 * Target information comes from LookIntent (where the player is looking).
 */
sealed class ClickIntent {
    /** Hold left click (mining, attacking). */
    object LeftClick : ClickIntent()

    /** Hold right click (place block, use item). */
    object RightClick : ClickIntent()

    /** No click input. */
    object None : ClickIntent()
}
