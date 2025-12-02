package maestro.pathing.path;

import java.util.*;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.PackedBlockPos;
import maestro.utils.pathing.PathBase;

public class SplicedPath extends PathBase {

    private final List<PackedBlockPos> path;

    private final List<IMovement> movements;

    private final int numNodes;

    private final Goal goal;

    private SplicedPath(
            List<PackedBlockPos> path,
            List<IMovement> movements,
            int numNodesConsidered,
            Goal goal) {
        this.path = path;
        this.movements = movements;
        this.numNodes = numNodesConsidered;
        this.goal = goal;
        sanityCheck();
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    @Override
    public List<IMovement> movements() {
        return Collections.unmodifiableList(movements);
    }

    @Override
    public List<PackedBlockPos> positions() {
        return Collections.unmodifiableList(path);
    }

    @Override
    public int getNumNodesConsidered() {
        return numNodes;
    }

    @Override
    public int length() {
        return path.size();
    }

    @Override
    public void replaceMovement(int index, IMovement newMovement) {
        if (index < 0 || index >= movements.size()) {
            throw new IndexOutOfBoundsException(
                    "Index "
                            + index
                            + " out of bounds for movements list of size "
                            + movements.size());
        }
        movements.set(index, newMovement);
    }

    public static Optional<SplicedPath> trySplice(
            IPath first, IPath second, boolean allowOverlapCutoff) {
        if (second == null || first == null) {
            return Optional.empty();
        }
        if (!first.getDest().equals(second.getSrc())) {
            return Optional.empty();
        }
        HashSet<PackedBlockPos> secondPos = new HashSet<>(second.positions());
        int firstPositionInSecond = -1;
        for (int i = 0;
                i < first.length() - 1;
                i++) { // overlap in the very last element is fine (and required) so only go up to
            // first.length() - 1
            if (secondPos.contains(first.positions().get(i))) {
                firstPositionInSecond = i;
                break;
            }
        }
        if (firstPositionInSecond != -1) {
            if (!allowOverlapCutoff) {
                return Optional.empty();
            }
        } else {
            firstPositionInSecond = first.length() - 1;
        }
        int positionInSecond =
                second.positions().indexOf(first.positions().get(firstPositionInSecond));
        if (!allowOverlapCutoff && positionInSecond != 0) {
            throw new IllegalStateException("Paths to be spliced are overlapping incorrectly");
        }
        List<PackedBlockPos> positions =
                new ArrayList<>(first.positions().subList(0, firstPositionInSecond + 1));
        List<IMovement> movements =
                new ArrayList<>(first.movements().subList(0, firstPositionInSecond));

        positions.addAll(second.positions().subList(positionInSecond + 1, second.length()));
        movements.addAll(second.movements().subList(positionInSecond, second.length() - 1));
        return Optional.of(
                new SplicedPath(
                        positions,
                        movements,
                        first.getNumNodesConsidered() + second.getNumNodesConsidered(),
                        first.getGoal()));
    }
}
