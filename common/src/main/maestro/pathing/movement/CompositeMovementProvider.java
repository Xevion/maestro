package maestro.pathing.movement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.utils.PackedBlockPos;

/**
 * Combines multiple movement providers. Allows mixing different movement generation strategies
 * (e.g., enum-based terrestrial movements + dynamic swimming).
 */
public class CompositeMovementProvider implements IMovementProvider {
    private final List<IMovementProvider> providers;

    public CompositeMovementProvider(IMovementProvider... providers) {
        this.providers = Arrays.asList(providers);
    }

    @Override
    public Stream<IMovement> generateMovements(CalculationContext context, PackedBlockPos from) {
        return providers.stream().flatMap(provider -> provider.generateMovements(context, from));
    }
}
