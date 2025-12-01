package maestro.pathing.recovery;

import java.util.Objects;
import maestro.api.utils.PackedBlockPos;

/**
 * Composite key representing a specific movement from source to destination.
 *
 * <p>Used to track failures for specific movement pairs, since a teleport failure from A→B doesn't
 * mean B is universally bad, just that the specific A→B movement failed.
 */
public class MovementKey {

    public final PackedBlockPos source;
    public final PackedBlockPos destination;

    public MovementKey(PackedBlockPos source, PackedBlockPos destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MovementKey that = (MovementKey) o;
        return (source.equals(that.source) && destination.equals(that.destination));
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination);
    }

    @Override
    public String toString() {
        return source + "→" + destination;
    }
}
