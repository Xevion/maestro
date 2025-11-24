package maestro.utils

import maestro.api.pathing.goals.Goal
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.pathing.movement.CalculationContext

/**
 * A pathing command that includes a desired calculation context.
 *
 * This extends the base PathingCommand to include additional context information
 * needed for calculating paths with specific requirements.
 *
 * @property goal The goal to path towards
 * @property commandType The type of pathing command
 * @property desiredCalcContext The calculation context to use for this command
 */
class PathingCommandContext(
    goal: Goal,
    commandType: PathingCommandType,
    @JvmField val desiredCalcContext: CalculationContext,
) : PathingCommand(goal, commandType)
