package maestro.utils

import maestro.pathing.calc.IPath
import java.util.Optional

/**
 * Represents the result of a pathfinding calculation.
 *
 * Contains an optional path and a result type indicating success, partial success,
 * failure, cancellation, or exception.
 *
 * @property type The result type of the pathfinding calculation
 * @property path The calculated path, if any
 */
data class PathCalculationResult
    @JvmOverloads
    constructor(
        @get:JvmName("getType") val type: Type,
        private val path: IPath? = null,
    ) {
        /**
         * Returns the calculated path wrapped in an Optional.
         *
         * @return Optional containing the path, or empty if no path was calculated
         */
        @JvmName("getPath")
        fun getPath(): Optional<IPath> = Optional.ofNullable(path)

        /**
         * Result types for path calculations.
         */
        enum class Type {
            /** Successfully reached the goal */
            SUCCESS_TO_GOAL,

            /** Successfully calculated a segment towards the goal */
            SUCCESS_SEGMENT,

            /** Failed to find a path */
            FAILURE,

            /** Calculation was cancelled */
            CANCELLATION,

            /** An exception occurred during calculation */
            EXCEPTION,
        }
    }
