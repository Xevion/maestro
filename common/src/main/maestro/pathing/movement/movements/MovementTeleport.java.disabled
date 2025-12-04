package maestro.pathing.movement.movements;

import java.util.Set;
import maestro.api.IAgent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.slf4j.Logger;

/**
 * Packet-based teleportation movement exploiting Minecraft's movement packet handling.
 *
 * <p>Sends filler "StatusOnly" packets followed by a position update to trick the server into
 * accepting large position jumps (up to 200 blocks).
 */
public class MovementTeleport extends Movement {
    private static final Logger log = MaestroLogger.get("move");

    private static final PackedBlockPos[] EMPTY = new PackedBlockPos[] {};

    // Cost constants
    private static final double BASE_COST = 5.0; // Very cheap to make teleports attractive
    private static final double DISTANCE_PENALTY = 0.05;
    private static final double PACKET_PENALTY = 0.1;

    // Track whether we've attempted the teleport
    private boolean attempted = false;

    // Teleport arrival position (where packets are sent, may be in air)
    private final PackedBlockPos teleportArrival;

    public MovementTeleport(
            IAgent maestro, PackedBlockPos src, PackedBlockPos arrival, PackedBlockPos landing) {
        super(maestro, src, landing, EMPTY); // dest is the landing position for pathfinding
        this.teleportArrival = arrival;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        // Calculate distance
        double distance = Math.sqrt(dest.distSqr(src));

        // Check distance bounds
        if (distance < context.teleportMinDistance || distance > context.teleportMaxDistance) {
            return ActionCosts.COST_INF;
        }

        // Calculate required packets
        int packetsRequired = Math.max(0, (int) Math.ceil(distance / 10.0) - 1);

        // Check packet limit (exploit limitation)
        if (packetsRequired > 19) {
            return ActionCosts.COST_INF;
        }

        // Validate destination
        if (!isValidDestination(context)) {
            return ActionCosts.COST_INF;
        }

        // Calculate fall height (difference between arrival and landing positions)
        int fallHeight = teleportArrival.getY() - dest.getY();

        // Add fall time cost (using Minecraft physics for accurate time calculation)
        double fallCost = fallHeight > 0 ? ActionCosts.FALL_N_BLOCKS_COST[fallHeight] : 0;

        // Calculate total cost
        double cost =
                BASE_COST
                        + (distance * DISTANCE_PENALTY)
                        + (packetsRequired * PACKET_PENALTY)
                        + fallCost;

        // Apply cost multiplier from settings
        cost *= context.teleportCostMultiplier;

        return cost;
    }

    private boolean isValidDestination(CalculationContext context) {
        // Check chunk loaded
        if (!context.bsi.worldContainsLoadedChunk(dest.getX(), dest.getZ())) {
            return false;
        }

        // Check world bounds
        if (!context.worldBorder.canPlaceAt(dest.getX(), dest.getZ())) {
            return false;
        }

        // Check 2x1 open space
        if (!MovementHelper.fullyPassable(context, dest.getX(), dest.getY(), dest.getZ())) {
            return false;
        }
        if (!MovementHelper.fullyPassable(context, dest.getX(), dest.getY() + 1, dest.getZ())) {
            return false;
        }

        // Check solid ground within 0-5 blocks below (air-drop support)
        int minY = context.world.getMinY();
        boolean foundGround = false;
        for (int depth = 1; depth <= 5; depth++) {
            int checkY = dest.getY() - depth;

            // Stop at world minimum to prevent checking below void
            if (checkY < minY) {
                break;
            }

            var state = context.get(dest.getX(), checkY, dest.getZ());

            if (MovementHelper.canWalkThrough(context, dest.getX(), checkY, dest.getZ(), state)) {
                // Passable block - check if it's dangerous (fire, lava, etc.)
                if (MovementHelper.avoidWalkingInto(state)) {
                    return false; // Dangerous passable block in air gap
                }
                continue; // Safe passable block, keep checking deeper
            }

            // Hit non-passable block - verify it's walkable
            if (!MovementHelper.canWalkOn(context, dest.getX(), checkY, dest.getZ(), state)) {
                return false; // Non-walkable solid block
            }

            // Found valid ground within 5 blocks - verify it's safe
            if (MovementHelper.avoidWalkingInto(state)) {
                return false; // Dangerous landing block
            }

            // Valid safe landing found
            foundGround = true;
            break;
        }

        return foundGround; // No ground found within 5 blocks
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        Set<PackedBlockPos> positions = new java.util.HashSet<>();
        positions.add(src);

        // Include all intermediate positions during fall from arrival to landing
        // If arrival and landing are at same Y, this just adds the landing position
        for (int y = teleportArrival.getY(); y >= dest.getY(); y--) {
            positions.add(new PackedBlockPos(dest.getX(), y, dest.getZ()));
        }

        return positions;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        // Check if already at destination
        if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        // Only attempt teleport once
        if (!attempted) {
            attempted = true;
            executeTeleport();

            // Give server a moment to process packets (check next tick)
            return state;
        }

        // After teleport attempt: check if player moved from source
        if (ctx.playerFeet().toBlockPos().equals(src.toBlockPos())) {
            // Still at source - teleport was rejected
            double distance = Math.sqrt(dest.distSqr(src));
            log.atWarn()
                    .addKeyValue("distance", String.format("%.1f", distance))
                    .addKeyValue("fall_height", teleportArrival.getY() - dest.getY())
                    .log("Teleport rejected by server");
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        // Player has moved - check if at destination (landed successfully)
        if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        // Player is falling - verify they're in a valid intermediate position
        if (!getValidPositions().contains(ctx.playerFeet())) {
            int offsetX = ctx.playerFeet().getX() - dest.getX();
            int offsetY = ctx.playerFeet().getY() - dest.getY();
            int offsetZ = ctx.playerFeet().getZ() - dest.getZ();
            log.atWarn()
                    .addKeyValue("offset_x", offsetX)
                    .addKeyValue("offset_y", offsetY)
                    .addKeyValue("offset_z", offsetZ)
                    .log("Teleport landed at unexpected position");
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        // Player is falling, stay in RUNNING state
        return state;
    }

    private void executeTeleport() {
        double distance = Math.sqrt(teleportArrival.distSqr(src));

        // Calculate required filler packets
        int packetsRequired = Math.max(0, (int) Math.ceil(distance / 10.0) - 1);

        // Send filler packets (StatusOnly)
        // IMPORTANT: Both params must be TRUE for the exploit to work
        for (int i = 0; i < packetsRequired; i++) {
            ctx.player().connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, true));
        }

        // Send final position update to arrival position (maybe in air)
        double arrivalX = teleportArrival.getX() + 0.5; // Center of block
        double arrivalY = teleportArrival.getY();
        double arrivalZ = teleportArrival.getZ() + 0.5; // Center of block

        // IMPORTANT: onGround=true, horizontalCollision=true (hardcoded for exploit)
        ctx.player()
                .connection
                .send(
                        new ServerboundMovePlayerPacket.Pos(
                                arrivalX, arrivalY, arrivalZ, true, true));

        // Update client-side position to arrival (player will fall to landing)
        ctx.player().setPos(arrivalX, arrivalY, arrivalZ);
    }

    @Override
    public void reset() {
        super.reset();
        attempted = false; // Reset attempt flag when movement is reset
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // Teleport is instant, safe to cancel before execution or if not yet attempted
        return state.getStatus() != MovementStatus.RUNNING || !attempted;
    }
}
