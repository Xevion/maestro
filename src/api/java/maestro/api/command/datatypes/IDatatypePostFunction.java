package maestro.api.command.datatypes;

import maestro.api.command.exception.CommandException;

public interface IDatatypePostFunction<T, O> {

    T apply(O original) throws CommandException;
}
