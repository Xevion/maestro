package maestro.api.pathing.calc;

import java.util.Optional;
import maestro.api.process.IMaestroProcess;
import maestro.api.process.PathingCommand;

public interface IPathingControlManager {

    /**
     * Registers a process with this pathing control manager. See {@link IMaestroProcess} for more
     * details.
     *
     * @param process The process
     * @see IMaestroProcess
     */
    void registerProcess(IMaestroProcess process);

    /**
     * @return The most recent {@link IMaestroProcess} that had control
     */
    Optional<IMaestroProcess> mostRecentInControl();

    /**
     * @return The most recent pathing command executed
     */
    Optional<PathingCommand> mostRecentCommand();
}
