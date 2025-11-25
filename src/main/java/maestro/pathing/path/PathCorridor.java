package maestro.pathing.path;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import maestro.api.pathing.calc.IPath;
import maestro.api.utils.BetterBlockPos;
import maestro.pathing.movement.Movement;

/**
 * Defines a spatial corridor around path segments to tolerate minor deviations.
 *
 * <p>Instead of requiring exact position on Movement.getValidPositions(), allows agent to be within
 * 1 block of any valid position. Reduces unnecessary replanning caused by water currents, combat
 * knockback, or imprecise movement.
 */
public class PathCorridor {
    private static final int CORRIDOR_BUFFER = 1; // Blocks of tolerance
    private static final int SEGMENT_WINDOW = 2; // Segments ahead/behind to include

    private final IPath path;
    private int currentSegment;
    private int corridorStart;
    private int corridorEnd;
    private Set<BetterBlockPos> corridorPositions;

    // Cache for nearest position lookup
    private BetterBlockPos cachedClosest;
    private double cachedClosestDist;
    private int cachedClosestSegment;

    /**
     * Creates a new path corridor.
     *
     * @param path Path to create corridor around
     * @param startingSegment Initial segment index
     */
    public PathCorridor(IPath path, int startingSegment) {
        this.path = path;
        this.currentSegment = startingSegment;
        rebuildCorridor();
    }

    /**
     * Updates the current segment and rebuilds corridor if window changed.
     *
     * @param newSegment New segment index
     */
    public void updateSegment(int newSegment) {
        if (newSegment == currentSegment) {
            return;
        }

        int oldStart = corridorStart;
        int oldEnd = corridorEnd;
        currentSegment = newSegment;

        corridorStart = Math.max(0, currentSegment - SEGMENT_WINDOW);
        corridorEnd = Math.min(path.movements().size() - 1, currentSegment + SEGMENT_WINDOW);

        if (corridorStart != oldStart || corridorEnd != oldEnd) {
            rebuildCorridor();
        }
    }

    /**
     * Checks if position is within corridor tolerance.
     *
     * @param pos Position to check
     * @return True if within corridor, false otherwise
     */
    public boolean isWithinCorridor(BetterBlockPos pos) {
        return corridorPositions.contains(pos);
    }

    /**
     * Finds the nearest valid position to the given position.
     *
     * @param from Position to find nearest valid position from
     * @return Optional containing nearest position, or empty if none found
     */
    public Optional<BetterBlockPos> findNearestValidPosition(BetterBlockPos from) {
        // Use cached result if still valid
        if (cachedClosest != null && cachedClosestSegment == currentSegment) {
            double cachedDist = from.distanceSq(cachedClosest);
            if (Math.abs(cachedDist - cachedClosestDist) < 4) {
                return Optional.of(cachedClosest);
            }
        }

        // Scan corridor window for nearest valid position
        BetterBlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int i = corridorStart; i <= corridorEnd && i < path.movements().size(); i++) {
            Movement movement = (Movement) path.movements().get(i);
            for (BetterBlockPos validPos : movement.getValidPositions()) {
                double distSq = from.distanceSq(validPos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = validPos;
                }
            }
        }

        if (nearest != null) {
            cachedClosest = nearest;
            cachedClosestDist = nearestDistSq;
            cachedClosestSegment = currentSegment;
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Calculates distance from position to nearest point on path.
     *
     * @param from Position to calculate distance from
     * @return Distance to path, or Double.MAX_VALUE if no valid positions
     */
    public double distanceToPath(BetterBlockPos from) {
        return findNearestValidPosition(from)
                .map(pos -> Math.sqrt(from.distanceSq(pos)))
                .orElse(Double.MAX_VALUE);
    }

    /** Rebuilds corridor positions for current window. */
    private void rebuildCorridor() {
        corridorPositions = new HashSet<>();

        for (int i = corridorStart; i <= corridorEnd && i < path.movements().size(); i++) {
            Movement movement = (Movement) path.movements().get(i);
            Set<BetterBlockPos> validPos = movement.getValidPositions();

            corridorPositions.addAll(validPos);

            for (BetterBlockPos pos : validPos) {
                addBuffer(pos);
            }
        }

        cachedClosest = null;
    }

    /**
     * Adds buffer positions around a center position.
     *
     * @param center Center position to add buffer around
     */
    private void addBuffer(BetterBlockPos center) {
        // Add 26 surrounding blocks (3x3x3 cube minus center)
        for (int dx = -CORRIDOR_BUFFER; dx <= CORRIDOR_BUFFER; dx++) {
            for (int dy = -CORRIDOR_BUFFER; dy <= CORRIDOR_BUFFER; dy++) {
                for (int dz = -CORRIDOR_BUFFER; dz <= CORRIDOR_BUFFER; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    corridorPositions.add(
                            new BetterBlockPos(center.x + dx, center.y + dy, center.z + dz));
                }
            }
        }
    }
}
