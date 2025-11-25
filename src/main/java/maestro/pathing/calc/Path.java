package maestro.pathing.calc;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.Helper;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.Moves;
import maestro.pathing.path.CutoffPath;
import maestro.utils.pathing.PathBase;

/**
 * A node based implementation of IPath
 *
 * @author leijurv
 */
class Path extends PathBase {

    /** The start position of this path */
    private final BetterBlockPos start;

    /** The end position of this path */
    private final BetterBlockPos end;

    /**
     * The blocks on the path. Guaranteed that path.get(0) equals start and path.get(path.size()-1)
     * equals end
     */
    private final List<BetterBlockPos> path;

    private final List<Movement> movements;

    private final List<PathNode> nodes;

    private final Goal goal;

    private final int numNodes;

    private final CalculationContext context;

    private volatile boolean verified;

    Path(
            BetterBlockPos realStart,
            PathNode start,
            PathNode end,
            int numNodes,
            Goal goal,
            CalculationContext context) {
        this.end = new BetterBlockPos(end.x, end.y, end.z);
        this.numNodes = numNodes;
        this.movements = new ArrayList<>();
        this.goal = goal;
        this.context = context;

        PathNode current = end;
        List<BetterBlockPos> tempPath = new ArrayList<>();
        List<PathNode> tempNodes = new ArrayList<>();
        while (current != null) {
            tempNodes.add(current);
            tempPath.add(new BetterBlockPos(current.x, current.y, current.z));
            current = current.previous;
        }

        // If the position the player is at is different from the position we told A* to start from,
        // and A* gave us no movements, then add a fake node that will allow a movement to be
        // created
        // that gets us to the single position in the path.
        // See PathingBehavior#createPathfinder and https://github.com/cabaletta/baritone/pull/4519
        var startNodePos = new BetterBlockPos(start.x, start.y, start.z);
        if (!realStart.equals(startNodePos) && start.equals(end)) {
            this.start = realStart;
            PathNode fakeNode = new PathNode(realStart.x, realStart.y, realStart.z, goal);
            fakeNode.cost = 0;
            tempNodes.add(fakeNode);
            tempPath.add(realStart);
        } else {
            this.start = startNodePos;
        }

        // Nodes are traversed last to first so we need to reverse the list
        this.path = Lists.reverse(tempPath);
        this.nodes = Lists.reverse(tempNodes);
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    private boolean assembleMovements() {
        if (path.isEmpty() || !movements.isEmpty()) {
            throw new IllegalStateException("Path must not be empty");
        }
        for (int i = 0; i < path.size() - 1; i++) {
            double cost = nodes.get(i + 1).cost - nodes.get(i).cost;
            PathNode nextNode = nodes.get(i + 1);

            // NEW: Check if movement was stored directly in node
            if (nextNode.previousMovement != null) {
                Movement move = (Movement) nextNode.previousMovement;
                // Verify destination matches (sanity check)
                if (move.getDest().equals(path.get(i + 1))) {
                    movements.add(move);
                    continue;
                } else {
                    // Shouldn't happen, but log and fallback
                    Helper.HELPER.logDebug(
                            "Stored movement destination mismatch: expected "
                                    + path.get(i + 1)
                                    + ", got "
                                    + move.getDest());
                }
            }

            // FALLBACK: Use old runBackwards method for compatibility
            Movement move = runBackwards(path.get(i), path.get(i + 1), nextNode, cost);
            if (move == null) {
                return true;
            } else {
                movements.add(move);
            }
        }
        return false;
    }

    private Movement runBackwards(
            BetterBlockPos src, BetterBlockPos dest, PathNode destNode, double cost) {
        // Try recorded movement first (O(1) lookup)
        Moves recordedMove = destNode.getMovement();
        if (recordedMove != null) {
            Movement move = recordedMove.apply0(context, src);
            if (move.getDest().equals(dest)) {
                // have to calculate the cost at calculation time so we can accurately judge whether
                // a cost increase happened between cached calculation and real execution
                // however, taking into account possible favoring that could skew the node cost, we
                // really want the stricter limit of the two
                // so we take the minimum of the path node cost difference, and the calculated cost
                move.override(Math.min(move.calculateCost(context), cost));
                return move;
            }
            // Recorded movement doesn't match destination (shouldn't happen)
            Helper.HELPER.logDebug(
                    "Recorded movement "
                            + recordedMove
                            + " from "
                            + src
                            + " doesn't match expected dest "
                            + dest);
        }

        // Fallback: Linear search through all movements (for backward compatibility)
        for (Moves moves : Moves.values()) {
            Movement move = moves.apply0(context, src);
            if (move.getDest().equals(dest)) {
                move.override(Math.min(move.calculateCost(context), cost));
                return move;
            }
        }

        // this is no longer called from bestPathSoFar, now it's in postprocessing
        Helper.HELPER.logDebug(
                "Movement became impossible during calculation "
                        + src
                        + " "
                        + dest
                        + " "
                        + dest.subtract(src));
        return null;
    }

    @Override
    public IPath postProcess() {
        if (verified) {
            throw new IllegalStateException("Path must not be verified twice");
        }
        verified = true;
        boolean failed = assembleMovements();
        movements.forEach(m -> m.checkLoadedChunk(context));

        if (failed) { // at least one movement became impossible during calculation
            CutoffPath res = new CutoffPath(this, movements().size());
            if (res.movements().size() != movements.size()) {
                throw new IllegalStateException("Path has wrong size after cutoff");
            }
            return res;
        }
        // more post processing here
        sanityCheck();
        return this;
    }

    @Override
    public List<IMovement> movements() {
        if (!verified) {
            // edge case note: this is called during verification
            throw new IllegalStateException("Path not yet verified");
        }
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
    public BetterBlockPos getSrc() {
        return start;
    }

    @Override
    public BetterBlockPos getDest() {
        return end;
    }
}
