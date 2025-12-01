package maestro.combat

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * Predicts moving target positions accounting for arrow flight time.
 * Uses iterative refinement to converge on accurate prediction.
 */
object TargetPredictor {
    /**
     * Predict where a moving target will be when arrow arrives.
     * Uses iterative refinement algorithm that converges in 2-3 iterations.
     *
     * @param target Entity to predict position for
     * @param shooterPos Shooter's eye position
     * @param arrowVelocity Arrow velocity in blocks/tick
     * @param iterations Number of refinement iterations (default 3)
     * @return Predicted position where target will be
     */
    fun predictPosition(
        target: Entity,
        shooterPos: Vec3,
        arrowVelocity: Double,
        iterations: Int = 3,
    ): Vec3 {
        val velocity = target.deltaMovement

        // If target is stationary, return current position
        if (velocity.lengthSqr() < 0.001) {
            return target.position()
        }

        // Guard against zero or very low arrow velocity
        if (arrowVelocity < 0.1) {
            return target.position()
        }

        // Initial estimate: time = distance / arrow speed
        var predictedPos = target.position()
        var flightTime = predictedPos.distanceTo(shooterPos) / arrowVelocity

        // Iterative refinement to account for target movement during flight
        repeat(iterations) {
            // Predict where target will be after flight time
            predictedPos = target.position().add(velocity.scale(flightTime))

            // Recalculate flight time to predicted position
            flightTime = predictedPos.distanceTo(shooterPos) / arrowVelocity
        }

        return predictedPos
    }

    /**
     * Predict target position with vertical offset for entity eye height.
     *
     * @param target Entity to predict position for
     * @param shooterPos Shooter's eye position
     * @param arrowVelocity Arrow velocity in blocks/tick
     * @param iterations Number of refinement iterations
     * @return Predicted eye-level position
     */
    fun predictEyePosition(
        target: Entity,
        shooterPos: Vec3,
        arrowVelocity: Double,
        iterations: Int = 3,
    ): Vec3 {
        val predictedPos = predictPosition(target, shooterPos, arrowVelocity, iterations)
        return predictedPos.add(0.0, target.eyeHeight * 0.5, 0.0)
    }

    /**
     * Check if target is moving predictably (constant velocity).
     * Currently, checks if target has any velocity; future enhancement
     * could track velocity history to detect erratic movement.
     *
     * @param target Entity to check
     * @return true if target movement is predictable
     */
    fun isPredictable(target: Entity): Boolean {
        // Target is predictable if it's moving (has velocity)
        // Stationary targets are handled separately
        return target.deltaMovement.lengthSqr() > 0.001
    }

    /**
     * Calculate approximate flight time for arrow to reach target.
     *
     * @param target Target entity
     * @param shooterPos Shooter position
     * @param arrowVelocity Arrow velocity
     * @return Estimated flight time in ticks
     */
    fun estimateFlightTime(
        target: Entity,
        shooterPos: Vec3,
        arrowVelocity: Double,
    ): Double {
        val distance = target.position().distanceTo(shooterPos)
        return distance / arrowVelocity
    }
}
