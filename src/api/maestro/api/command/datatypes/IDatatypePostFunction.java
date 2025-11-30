package maestro.api.command.datatypes;

public interface IDatatypePostFunction<T, O> {

    T apply(O original);
}
