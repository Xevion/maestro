package maestro.api.command.datatypes;

import maestro.Agent;
import maestro.api.command.argument.IArgConsumer;

/**
 * Provides an {@link IDatatype} with contextual information so that it can perform the desired
 * operation on the target level.
 *
 * @see IDatatype
 */
public interface IDatatypeContext {

    /**
     * Provides the {@link Agent} instance that is associated with the action relating to datatype
     * handling.
     *
     * @return The context {@link Agent} instance.
     */
    Agent getMaestro();

    /**
     * Provides the {@link IArgConsumer}} to fetch input information from.
     *
     * @return The context {@link IArgConsumer}}.
     */
    IArgConsumer getConsumer();
}
