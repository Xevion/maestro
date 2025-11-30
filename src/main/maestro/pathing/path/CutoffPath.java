package maestro.pathing.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.BetterBlockPos;
import maestro.utils.pathing.PathBase;

public class CutoffPath extends PathBase {

    private final List<BetterBlockPos> path;

    private final List<IMovement> movements;

    private final int numNodes;

    private final Goal goal;

    public CutoffPath(IPath prev, int firstPositionToInclude, int lastPositionToInclude) {
        path = prev.positions().subList(firstPositionToInclude, lastPositionToInclude + 1);
        movements =
                new ArrayList<>(
                        prev.movements().subList(firstPositionToInclude, lastPositionToInclude));
        numNodes = prev.getNumNodesConsidered();
        goal = prev.getGoal();
        sanityCheck();
    }

    public CutoffPath(IPath prev, int lastPositionToInclude) {
        this(prev, 0, lastPositionToInclude);
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
    public List<BetterBlockPos> positions() {
        return Collections.unmodifiableList(path);
    }

    @Override
    public int getNumNodesConsidered() {
        return numNodes;
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
}
