package maestro.utils;

@FunctionalInterface
public interface BooleanBinaryOperator {

    boolean applyAsBoolean(boolean a, boolean b);
}
