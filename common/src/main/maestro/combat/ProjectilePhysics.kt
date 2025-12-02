package maestro.combat

import maestro.api.combat.TrajectoryResult
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ballistic physics calculations for arrow trajectories.
 */
object ProjectilePhysics {
    // Physics constants (verified from Minecraft source)
    const val ARROW_GRAVITY = 0.05 // blocks/tick²
    const val ARROW_DRAG = 0.01 // velocity reduction per tick
    const val MAX_VELOCITY = 3.0 // fully charged bow
    const val TICKS_FOR_FULL_CHARGE = 20

    // Math constants
    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    /**
     * Calculate pitch angle needed to hit target position using ballistic equation.
     *
     * @param origin Shooter's eye position
     * @param target Target position to hit
     * @param velocity Arrow velocity based on bow charge
     * @return Pitch in degrees, or null if target is unreachable
     */
    fun calculatePitch(
        origin: Vec3,
        target: Vec3,
        velocity: Double,
    ): Float? {
        val horizontal = sqrt((target.x - origin.x).pow(2) + (target.z - origin.z).pow(2))
        val vertical = target.y - origin.y

        // Quadratic formula for ballistic trajectory
        // Solves: tan(θ) = (v² ± √(v⁴ - g(gd² + 2hv²))) / gd
        val vSq = velocity * velocity
        val g = ARROW_GRAVITY
        val discriminant = vSq * vSq - g * (g * horizontal * horizontal + 2 * vertical * vSq)

        if (discriminant < 0) return null // Target unreachable with this velocity

        // Use negative sqrt for lower trajectory (more accurate and direct)
        // Negate result because Minecraft pitch: positive = down, negative = up
        val pitch = -atan((vSq - sqrt(discriminant)) / (g * horizontal))
        return (pitch * RAD_TO_DEG).toFloat()
    }

    /**
     * Calculate yaw angle to horizontal target position.
     *
     * @param origin Shooter position
     * @param target Target position
     * @return Yaw in degrees
     */
    fun calculateYaw(
        origin: Vec3,
        target: Vec3,
    ): Float {
        val deltaX = target.x - origin.x
        val deltaZ = target.z - origin.z
        val yaw = atan2(-deltaX, deltaZ)
        return (yaw * RAD_TO_DEG).toFloat()
    }

    /**
     * Simulate arrow trajectory with step-by-step physics and collision detection.
     * Returns list of points along path for rendering and analysis.
     *
     * @param origin Starting position (shooter's eye position)
     * @param yaw Horizontal angle in degrees
     * @param pitch Vertical angle in degrees
     * @param velocity Initial arrow velocity
     * @param world World for collision detection
     * @param maxTicks Maximum simulation steps (default 200)
     * @return TrajectoryResult containing path points and hit information
     */
    fun simulateTrajectory(
        origin: Vec3,
        yaw: Float,
        pitch: Float,
        velocity: Double,
        world: Level,
        maxTicks: Int = 200,
    ): TrajectoryResult {
        val points = mutableListOf<Vec3>()
        var pos = origin
        var vel = calculateVelocity(yaw, pitch, velocity)

        for (tick in 0 until maxTicks) {
            points.add(pos)

            // Calculate next position
            val nextPos = pos.add(vel)

            // Check collision with blocks
            val hitResult =
                world.clip(
                    ClipContext(
                        pos,
                        nextPos,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext
                            .empty(),
                    ),
                )

            if (hitResult.type == HitResult.Type.BLOCK) {
                points.add(hitResult.location)
                return TrajectoryResult(points, hitResult.location, true)
            }

            // Update position and velocity
            pos = nextPos
            vel = vel.subtract(0.0, ARROW_GRAVITY, 0.0) // Apply gravity
            vel = vel.scale(1.0 - ARROW_DRAG) // Apply air resistance

            // Stop if arrow falls below world
            if (pos.y < world.dimensionType().minY().toDouble()) {
                break
            }
        }

        return TrajectoryResult(points, pos, false)
    }

    /**
     * Convert bow charge ticks to arrow velocity.
     * Uses Minecraft's bow charging formula.
     *
     * @param chargeTicks Number of ticks bow has been charged
     * @return Arrow velocity in blocks/tick
     */
    fun getVelocityFromCharge(chargeTicks: Int): Double {
        val progress = min(chargeTicks.toFloat() / TICKS_FOR_FULL_CHARGE.toFloat(), 1.0f)
        // Minecraft formula: (progress² + progress × 2) / 3 × max_velocity
        return ((progress * progress + progress * 2.0) / 3.0) * MAX_VELOCITY
    }

    /**
     * Calculate velocity vector from yaw, pitch, and speed.
     *
     * @param yaw Horizontal angle in degrees
     * @param pitch Vertical angle in degrees
     * @param speed Arrow speed magnitude
     * @return Velocity vector
     */
    private fun calculateVelocity(
        yaw: Float,
        pitch: Float,
        speed: Double,
    ): Vec3 {
        val yawRad = yaw * DEG_TO_RAD
        val pitchRad = pitch * DEG_TO_RAD

        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)

        return Vec3(x, y, z).normalize().scale(speed)
    }
}
