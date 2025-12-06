package maestro.command.datatypes;

public interface IDatatypePostFunction<T, O> {

    T apply(O original);
}
