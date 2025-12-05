package maestro.task.elytra;

import dev.babbaj.pathfinder.PathSegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.api.utils.PackedBlockPos;

public final class UnpackedSegment {

    private final Stream<PackedBlockPos> path;
    private final boolean finished;

    public UnpackedSegment(Stream<PackedBlockPos> path, boolean finished) {
        this.path = path;
        this.finished = finished;
    }

    public UnpackedSegment append(Stream<PackedBlockPos> other, boolean otherFinished) {
        // The new segment is only finished if the one getting added on is
        return new UnpackedSegment(Stream.concat(this.path, other), otherFinished);
    }

    public UnpackedSegment prepend(Stream<PackedBlockPos> other) {
        return new UnpackedSegment(Stream.concat(other, this.path), this.finished);
    }

    public List<PackedBlockPos> collect() {
        final List<PackedBlockPos> path = this.path.collect(Collectors.toList());

        // Remove backtracks
        final Map<PackedBlockPos, Integer> positionFirstSeen = new HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            PackedBlockPos pos = path.get(i);
            if (positionFirstSeen.containsKey(pos)) {
                int j = positionFirstSeen.get(pos);
                while (i > j) {
                    path.remove(i);
                    i--;
                }
            } else {
                positionFirstSeen.put(pos, i);
            }
        }

        return path;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public static UnpackedSegment from(final PathSegment segment) {
        return new UnpackedSegment(
                Arrays.stream(segment.packed).mapToObj(PackedBlockPos::new), segment.finished);
    }
}
