package maestro.pathing.movement;

import java.util.ArrayList;
import java.util.List;
import maestro.api.utils.Loggers;
import maestro.api.utils.PackedBlockPos;
import maestro.utils.BlockPosExtKt;
import maestro.utils.Vec3ExtKt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Finds valid teleport destinations using Fibonacci sphere sampling.
 *
 * <p>Generates evenly distributed candidate positions on spherical shells at various distances,
 * then validates each for safety and accessibility.
 */
public class TeleportDestinationFinder {
    private static final Logger log = Loggers.get("path");

    private static final double GOLDEN_RATIO = (1.0 + Math.sqrt(5.0)) / 2.0;

    /**
     * Result of teleport destination validation.
     *
     * <p>Contains both validation status and the Y coordinate of the ground block where the player
     * will land (player stands at groundBlockY + 1).
     */
    private static class ValidationResult {
        final boolean valid;
        final int groundBlockY; // Y coordinate of ground block (player stands at groundBlockY + 1)

        ValidationResult(boolean valid, int groundBlockY) {
            this.valid = valid;
            this.groundBlockY = groundBlockY;
        }
    }

    /**
     * A valid teleport destination with both arrival and landing positions.
     *
     * <p>Arrival is where the teleport packets are sent (perhaps in the air). Landing is where the
     * player ends up after falling (used as pathfinding dest).
     */
    public static class TeleportDestination {
        public final PackedBlockPos arrival; // Position where teleport packets are sent
        public final PackedBlockPos landing; // Position where player lands (= pathfinding dest)

        public TeleportDestination(PackedBlockPos arrival, PackedBlockPos landing) {
            this.arrival = arrival;
            this.landing = landing;
        }
    }

    /**
     * Find all valid teleport destinations from a given source position.
     *
     * @param context Calculation context with world state and settings
     * @param src Source position to teleport from
     * @return List of valid teleport destinations (arrival and landing positions)
     */
    public static List<TeleportDestination> findDestinations(
            CalculationContext context, PackedBlockPos src) {
        List<TeleportDestination> destinations = new ArrayList<>();

        int minDist = context.teleportMinDistance;
        int maxDist = context.teleportMaxDistance;
        int pointsPerRadius = 32;

        int totalCandidates = 0;
        int validCandidates = 0;

        // Sample at multiple radii (every 20 blocks)
        for (int radius = minDist; radius <= maxDist; radius += 20) {
            // Generate points on sphere using Fibonacci spiral
            for (int i = 0; i < pointsPerRadius; i++) {
                double theta = 2 * Math.PI * i / GOLDEN_RATIO;
                double phi = Math.acos(1 - 2 * (i + 0.5) / pointsPerRadius);

                int dx = (int) Math.round(radius * Math.sin(phi) * Math.cos(theta));
                int dy = (int) Math.round(radius * Math.cos(phi));
                int dz = (int) Math.round(radius * Math.sin(phi) * Math.sin(theta));

                PackedBlockPos candidate =
                        new PackedBlockPos(src.getX() + dx, src.getY() + dy, src.getZ() + dz);
                totalCandidates++;

                ValidationResult result = isValidDestination(context, candidate);
                if (result.valid) {
                    // Arrival is the sampled position, landing is one block above ground
                    PackedBlockPos landingPos =
                            new PackedBlockPos(
                                    candidate.getX(), result.groundBlockY + 1, candidate.getZ());
                    destinations.add(new TeleportDestination(candidate, landingPos));
                    validCandidates++;
                }
            }
        }

        // Debug logging removed for performance - was causing massive lag

        return destinations;
    }

    /**
     * Check if a position is a valid teleport destination.
     *
     * <p>Validation order optimized for early exits - fast checks first, expensive raycasts last.
     *
     * @param context Calculation context
     * @param pos Position to check
     * @return ValidationResult containing validity and ground block Y coordinate
     */
    private static ValidationResult isValidDestination(
            CalculationContext context, PackedBlockPos pos) {
        // Early exit: Chunk loaded (can't validate unloaded chunks)
        if (!context.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
            return new ValidationResult(false, -1);
        }

        // Early exit: World bounds
        if (!context.worldBorder.canPlaceAt(pos.getX(), pos.getZ())) {
            return new ValidationResult(false, -1);
        }

        // Early exit: Y bounds
        if (pos.getY() < context.world.getMinY() || pos.getY() > context.world.getMaxY() - 2) {
            return new ValidationResult(false, -1);
        }

        // Early exit: 2x1 open space (feet and head)
        if (!MovementValidation.fullyPassable(context, pos.getX(), pos.getY(), pos.getZ())) {
            return new ValidationResult(false, -1);
        }
        if (!MovementValidation.fullyPassable(context, pos.getX(), pos.getY() + 1, pos.getZ())) {
            return new ValidationResult(false, -1);
        }

        // Early exit: Solid ground within 0-5 blocks below (air-drop support)
        int minY = context.world.getMinY();
        int groundBlockY = -1;
        for (int depth = 1; depth <= 5; depth++) {
            int checkY = pos.getY() - depth;

            // Stop at world minimum to prevent checking below void
            if (checkY < minY) {
                break;
            }

            var state = context.get(pos.getX(), checkY, pos.getZ());

            if (MovementValidation.canWalkThrough(context, pos.getX(), checkY, pos.getZ(), state)) {
                // Passable block - check if it's dangerous (fire, lava, etc.)
                if (MovementValidation.avoidWalkingInto(state)) {
                    return new ValidationResult(false, -1); // Dangerous passable block in air gap
                }
                continue; // Safe passable block, keep checking deeper
            }

            // Hit non-passable block - verify it's walkable
            if (!MovementValidation.canWalkOn(context, pos.getX(), checkY, pos.getZ(), state)) {
                return new ValidationResult(false, -1); // Non-walkable solid block
            }

            // Found valid ground within 5 blocks - verify it's safe
            if (MovementValidation.avoidWalkingInto(state)) {
                return new ValidationResult(false, -1); // Dangerous landing block
            }

            // Valid safe landing found - store ground block Y
            groundBlockY = checkY;
            break;
        }

        if (groundBlockY == -1) {
            return new ValidationResult(false, -1); // No ground found within 5 blocks
        }

        // Expensive check: Line-of-sight raycast
        if (!hasLineOfSight(context, pos)) {
            return new ValidationResult(false, -1);
        }

        // Expensive check: Collision box validation
        if (!isCollisionFree(context, pos)) {
            return new ValidationResult(false, -1);
        }

        return new ValidationResult(true, groundBlockY);
    }

    /**
     * Check if there is a collision-free path from player to destination.
     *
     * <p>Uses swept AABB to validate the entire movement path, catching diagonal edge collisions
     * that single-point raycasts would miss. The player's bounding box (0.6 wide × 1.8 tall) is
     * expanded to cover the full movement path.
     *
     * @param context Calculation context
     * @param dest Destination position
     * @return true if player can teleport without collision
     */
    private static boolean hasLineOfSight(CalculationContext context, PackedBlockPos dest) {
        Vec3 srcPos = context.maestro.getPlayerContext().player().position();
        Vec3 destCenter = BlockPosExtKt.getCenter(dest.toBlockPos());

        // Phase 1: Quick center-point raycast as fast pre-filter
        Vec3 srcFeet = srcPos;
        Vec3 destFeet = destCenter;

        HitResult feetResult =
                context.world.clip(
                        new ClipContext(
                                srcFeet,
                                destFeet,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                context.maestro.getPlayerContext().player()));

        // Early exit if even the center path is blocked
        if (feetResult.getType() != HitResult.Type.MISS) {
            return false;
        }

        // Phase 1.5: Eyes-to-eyes raycast for proper line-of-sight validation
        // Player standing eye height is 1.62 blocks above feet
        Vec3 srcEyes = Vec3ExtKt.withY(srcPos, srcPos.y + 1.62);
        Vec3 destEyes = BlockPosExtKt.getCenterWithEyes(dest.toBlockPos());

        HitResult eyesResult =
                context.world.clip(
                        new ClipContext(
                                srcEyes,
                                destEyes,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                context.maestro.getPlayerContext().player()));

        // Early exit if eyes don't have line-of-sight (blocked by head-level obstacles)
        if (eyesResult.getType() != HitResult.Type.MISS) {
            return false;
        }

        // Phase 2: Swept AABB for comprehensive diagonal coverage
        // Create box encompassing entire movement path with full player width
        // Player dimensions: 0.6 blocks wide (±0.3 from center), 1.8 blocks tall
        double minX = Math.min(srcPos.x - 0.3, destCenter.x - 0.3);
        double minY = Math.min(srcPos.y, destCenter.y);
        double minZ = Math.min(srcPos.z - 0.3, destCenter.z - 0.3);

        double maxX = Math.max(srcPos.x + 0.3, destCenter.x + 0.3);
        double maxY = Math.max(srcPos.y + 1.8, destCenter.y + 1.8);
        double maxZ = Math.max(srcPos.z + 0.3, destCenter.z + 0.3);

        AABB sweptBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        VoxelShape sweptShape = Shapes.create(sweptBox);

        // Check if entire swept path is unobstructed
        // Player entity parameter excludes player's current position from collision check
        return context.world.isUnobstructed(
                context.maestro.getPlayerContext().player(), sweptShape);
    }

    /**
     * Check if player's bounding box would be collision-free at destination.
     *
     * <p>Uses Minecraft's native collision detection to check if player AABB intersects with any
     * blocks at destination. More accurate than manual neighbor checking.
     *
     * @param context Calculation context
     * @param dest Destination position
     * @return true if no collision detected
     */
    private static boolean isCollisionFree(CalculationContext context, PackedBlockPos dest) {
        // Create player bounding box at destination
        // Player dimensions: 0.6 width × 1.8 height (standing)
        Vec3 center = BlockPosExtKt.getCenter(dest.toBlockPos());

        AABB playerBox =
                new AABB(
                        center.x - 0.3,
                        center.y,
                        center.z - 0.3,
                        center.x + 0.3,
                        center.y + 1.8,
                        center.z + 0.3);

        // Convert AABB to VoxelShape for collision testing
        VoxelShape playerShape = Shapes.create(playerBox);

        // Check if the shape is unobstructed by blocks
        // Pass player entity to exclude player's current position from collision check
        // isUnobstructed returns true if there are NO collisions
        return context.world.isUnobstructed(
                context.maestro.getPlayerContext().player(), playerShape);
    }
}
