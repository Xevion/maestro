package maestro.api.behavior

import maestro.api.pathing.calc.IPath
import maestro.api.pathing.calc.IPathFinder
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.path.IPathExecutor
import java.util.Optional

interface IPathingBehavior : IBehavior {
    /**
     * Returns the estimated remaining ticks in the current pathing segment. Given that the return
     * type is an optional, [Optional.empty] will be returned in the case that there is no current
     * segment being pathed.
     *
     * @return The estimated remaining ticks in the current segment.
     */
    fun ticksRemainingInSegment(): Optional<Double> = ticksRemainingInSegment(true)

    /**
     * Returns the estimated remaining ticks in the current pathing segment. Given that the return
     * type is an optional, [Optional.empty] will be returned in the case that there is no current
     * segment being pathed.
     *
     * @param includeCurrentMovement whether to include the entirety of the cost of the currently
     *   executing movement in the total
     * @return The estimated remaining ticks in the current segment.
     */
    fun ticksRemainingInSegment(includeCurrentMovement: Boolean): Optional<Double> {
        val current = getCurrent() ?: return Optional.empty()
        val start = if (includeCurrentMovement) current.position else current.position + 1
        return Optional.of(current.path.ticksRemainingFrom(start))
    }

    /**
     * Returns the estimated remaining ticks to the current goal. Given that the return type is an
     * optional, [Optional.empty] will be returned in the case that there is no current goal.
     *
     * @return The estimated remaining ticks to the current goal.
     */
    fun estimatedTicksToGoal(): Optional<Double?>

    /** @return The current pathing goal */
    fun getGoal(): Goal?

    /**
     * @return Whether a path is currently being executed. This will be false if there's currently a
     *   pause.
     * @see hasPath
     */
    fun isPathing(): Boolean

    /**
     * @return If there is a current path. Note that the path is not necessarily being executed, for
     *   example when there is a pause in effect.
     * @see isPathing
     */
    fun hasPath(): Boolean = getCurrent() != null

    /**
     * Cancels the pathing behavior or the current path calculation, and all processes that could be
     * controlling path.
     *
     * Basically, "MAKE IT STOP".
     *
     * @return Whether the pathing behavior was canceled. All processes are guaranteed to be
     *   canceled, but the PathingBehavior might be in the middle of an uncancelable action like a
     *   parkour jump
     */
    fun cancelEverything(): Boolean

    /**
     * PLEASE never call this
     *
     * If cancelEverything was like "kill" this is "sudo kill -9". Or shutting off your computer.
     */
    fun forceCancel()

    /**
     * Returns the current path, from the current path executor, if there is one.
     *
     * @return The current path
     */
    fun getPath(): Optional<IPath> = Optional.ofNullable(getCurrent()).map { it.path }

    /** @return The current pathfinder being executed */
    fun getInProgress(): Optional<out IPathFinder?>

    /** @return The current path executor */
    fun getCurrent(): IPathExecutor?

    /** Returns the next path executor, created when planning ahead. @return The next path executor */
    fun getNext(): IPathExecutor?
}
