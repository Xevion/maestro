package maestro.api.command.datatypes;

import maestro.api.IAgent;
import maestro.api.command.argument.IArgConsumer;

/**
 * Provides an {@link IDatatype} with contextual information so that it can perform the desired
 * operation on the target level.
 *
 * @see IDatatype
 */
public interface IDatatypeContext {

    /**
     * Provides the {@link IAgent} instance that is associated with the action relating to datatype
     * handling.
     *
     * @return The context {@link IAgent} instance.
     */
    IAgent getMaestro();

    /**
     * Provides the {@link IArgConsumer}} to fetch input information from.
     *
     * @return The context {@link IArgConsumer}}.
     */
    IArgConsumer getConsumer();
}
