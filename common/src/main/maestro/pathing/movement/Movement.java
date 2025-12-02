package maestro.pathing.movement;

import com.google.common.collect.ImmutableList;
import java.util.*;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.IMovement;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.*;
import maestro.api.utils.input.Input;
import maestro.behavior.PathingBehavior;
import maestro.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;

public abstract class Movement implements IMovement, MovementHelper {

    public static final ImmutableList<Direction>
            HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP =
                    ImmutableList.of(
                            Direction.NORTH,
                            Direction.SOUTH,
                            Direction.EAST,
                            Direction.WEST,
                            Direction.DOWN);

    protected final IAgent maestro;
    protected final IPlayerContext ctx;

    private MovementState currentState = new MovementState().setStatus(MovementStatus.PREPPING);

    protected final PackedBlockPos src;

    protected final PackedBlockPos dest;

    /** The positions that need to be broken before this movement can ensue */
    protected final PackedBlockPos[] positionsToBreak;

    /** The position where we need to place a block before this movement can ensue */
    protected final PackedBlockPos positionToPlace;

    private Double cost;

    public List<BlockPos> toBreakCached = null;
    public List<BlockPos> toPlaceCached = null;
    public List<BlockPos> toWalkIntoCached = null;

    private Set<PackedBlockPos> validPositionsCached = null;

    private Boolean calculatedWhileLoaded;

    protected Movement(
            IAgent maestro,
            PackedBlockPos src,
            PackedBlockPos dest,
            PackedBlockPos[] toBreak,
            PackedBlockPos toPlace) {
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
        this.src = src;
        this.dest = dest;
        this.positionsToBreak = toBreak;
        this.positionToPlace = toPlace;
    }

    protected Movement(
            IAgent maestro, PackedBlockPos src, PackedBlockPos dest, PackedBlockPos[] toBreak) {
        this(maestro, src, dest, toBreak, null);
    }

    @Override
    public double getCost() throws NullPointerException {
        return cost;
    }

    public double getCost(CalculationContext context) {
        if (cost == null) {
            cost = calculateCost(context);
        }
        return cost;
    }

    public abstract double calculateCost(CalculationContext context);

    public double recalculateCost(CalculationContext context) {
        cost = null;
        return getCost(context);
    }

    public void override(double cost) {
        this.cost = cost;
    }

    /**
     * Get the Moves enum ordinal for this movement type. Used for backward compatibility when
     * storing movement ordinals in PathNode. Subclasses created from Moves enum should override to
     * return the corresponding ordinal.
     *
     * @return Moves enum ordinal, or -1 if not from Moves enum
     */
    public int getMovesOrdinal() {
        // Default: unknown (for movements not from Moves enum)
        // Subclasses should override if they correspond to a Moves enum value
        return -1;
    }

    protected abstract Set<PackedBlockPos> calculateValidPositions();

    public Set<PackedBlockPos> getValidPositions() {
        if (validPositionsCached == null) {
            validPositionsCached = calculateValidPositions();
            Objects.requireNonNull(validPositionsCached);
        }
        return validPositionsCached;
    }

    protected boolean playerInValidPosition() {
        return getValidPositions().contains(ctx.playerFeet())
                || getValidPositions()
                        .contains(((PathingBehavior) maestro.getPathingBehavior()).pathStart());
    }

    /**
     * Handles the execution of the latest Movement State, and offers a Status to the calling class.
     *
     * @return Status
     */
    @Override
    public MovementStatus update() {
        ctx.player().getAbilities().flying = false;
        currentState = updateState(currentState);

        // Swimming velocity is applied via RotationManager callback in updateState()
        // Only handle non-swimming water cases here
        if (MovementHelper.isLiquid(ctx, ctx.playerFeet().toBlockPos())) {
            if (!((Agent) maestro).getSwimmingBehavior().shouldActivateSwimming()) {
                // Fallback: vanilla treading water when enhanced swimming disabled
                if (ctx.player().position().y < dest.getY() + 0.6) {
                    currentState.setInput(Input.JUMP, true);
                }
            }
        } else {
            // Deactivate swimming mode when exiting water
            ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
        }
        if (ctx.player().isInWall()) {
            ctx.getSelectedBlock()
                    .ifPresent(
                            pos ->
                                    MovementHelper.switchToBestToolFor(
                                            ctx, BlockStateInterface.get(ctx, pos)));
            currentState.setInput(Input.CLICK_LEFT, true);
        }

        // If the movement target has to force the new rotations, or we aren't using silent move,
        // then force the rotations
        currentState
                .getTarget()
                .getRotation()
                .ifPresent(
                        rotation ->
                                maestro.getLookBehavior()
                                        .updateTarget(
                                                rotation,
                                                currentState.getTarget().hasToForceRotations()));
        maestro.getInputOverrideHandler().clearAllKeys();
        currentState
                .getInputStates()
                .forEach(
                        (input, forced) -> {
                            maestro.getInputOverrideHandler().setInputForceState(input, forced);
                        });
        currentState.getInputStates().clear();

        // If the current status indicates a completed movement
        if (currentState.getStatus().isComplete()) {
            // Only clear keys if not swimming - preserve swimming inputs across transitions
            Agent agent = (Agent) maestro;
            if (!agent.getSwimmingBehavior().shouldActivateSwimming()) {
                maestro.getInputOverrideHandler().clearAllKeys();
            }

            // Swimming stays active across movement transitions
            // Only deactivates when exiting water (line 139) or path ends (PathingBehavior)
        }

        return currentState.getStatus();
    }

    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }
        boolean somethingInTheWay = false;
        for (PackedBlockPos blockPos : positionsToBreak) {
            if (!ctx.world()
                            .getEntitiesOfClass(
                                    FallingBlockEntity.class,
                                    new AABB(0, 0, 0, 1, 1.1, 1).move(blockPos.toBlockPos()))
                            .isEmpty()
                    && Agent.settings().pauseMiningForFallingBlocks.value) {
                return false;
            }
            if (!MovementHelper.canWalkThrough(ctx, blockPos)) { // can't break air, so don't try
                somethingInTheWay = true;
                MovementHelper.switchToBestToolFor(
                        ctx, BlockStateInterface.get(ctx, blockPos.toBlockPos()));
                Optional<Rotation> reachable =
                        RotationUtils.reachable(
                                ctx,
                                blockPos.toBlockPos(),
                                ctx.playerController().getBlockReachDistance());
                if (reachable.isPresent()) {
                    Rotation rotTowardsBlock = reachable.get();
                    state.setTarget(new MovementState.MovementTarget(rotTowardsBlock, true));
                    if (ctx.isLookingAt(blockPos.toBlockPos())
                            || ctx.playerRotations().isReallyCloseTo(rotTowardsBlock)) {
                        state.setInput(Input.CLICK_LEFT, true);
                    }
                    return false;
                }
                state.setTarget(
                        new MovementState.MovementTarget(
                                RotationUtils.calcRotationFromVec3d(
                                        ctx.playerHead(),
                                        VecUtils.getBlockPosCenter(blockPos.toBlockPos()),
                                        ctx.playerRotations()),
                                true));
                // don't check selectedblock on this one, this is a fallback when we can't see any
                // face directly, it's intended to be breaking the "incorrect" block
                state.setInput(Input.CLICK_LEFT, true);
                return false;
            }
        }
        if (somethingInTheWay) {
            // There's a block or blocks that we can't walk through, but we have no target rotation
            // to reach any
            // So don't return true, actually set state to unreachable
            state.setStatus(MovementStatus.UNREACHABLE);
            return true;
        }
        return true;
    }

    @Override
    public boolean safeToCancel() {
        return safeToCancel(currentState);
    }

    protected boolean safeToCancel(MovementState currentState) {
        return true;
    }

    @Override
    public PackedBlockPos getSrc() {
        return src;
    }

    @Override
    public PackedBlockPos getDest() {
        return dest;
    }

    @Override
    public void reset() {
        currentState = new MovementState().setStatus(MovementStatus.PREPPING);
    }

    /**
     * Calculate latest movement state. Gets called once a tick.
     *
     * @param state The current state
     * @return The new state
     */
    public MovementState updateState(MovementState state) {
        if (!prepared(state)) {
            return state.setStatus(MovementStatus.PREPPING);
        } else if (state.getStatus() == MovementStatus.PREPPING) {
            state.setStatus(MovementStatus.WAITING);
        }

        if (state.getStatus() == MovementStatus.WAITING) {
            state.setStatus(MovementStatus.RUNNING);
        }

        return state;
    }

    @Override
    public BlockPos getDirection() {
        return getDest().toBlockPos().subtract(getSrc().toBlockPos());
    }

    public void checkLoadedChunk(CalculationContext context) {
        calculatedWhileLoaded = context.bsi.worldContainsLoadedChunk(dest.getX(), dest.getZ());
    }

    @Override
    public boolean calculatedWhileLoaded() {
        return calculatedWhileLoaded;
    }

    @Override
    public void resetBlockCache() {
        toBreakCached = null;
        toPlaceCached = null;
        toWalkIntoCached = null;
    }

    public List<BlockPos> toBreak(BlockStateInterface bsi) {
        if (toBreakCached != null) {
            return toBreakCached;
        }
        List<BlockPos> result = new ArrayList<>();
        for (PackedBlockPos positionToBreak : positionsToBreak) {
            if (!MovementHelper.canWalkThrough(
                    bsi, positionToBreak.getX(), positionToBreak.getY(), positionToBreak.getZ())) {
                result.add(positionToBreak.toBlockPos());
            }
        }
        toBreakCached = result;
        return result;
    }

    public List<BlockPos> toPlace(BlockStateInterface bsi) {
        if (toPlaceCached != null) {
            return toPlaceCached;
        }
        List<BlockPos> result = new ArrayList<>();
        if (positionToPlace != null
                && !MovementHelper.canWalkOn(
                        bsi,
                        positionToPlace.getX(),
                        positionToPlace.getY(),
                        positionToPlace.getZ())) {
            result.add(positionToPlace.toBlockPos());
        }
        toPlaceCached = result;
        return result;
    }

    public List<BlockPos> toWalkInto(BlockStateInterface bsi) { // overridden by movementdiagonal
        if (toWalkIntoCached == null) {
            toWalkIntoCached = new ArrayList<>();
        }
        return toWalkIntoCached;
    }

    public BlockPos[] toBreakAll() {
        return java.util.Arrays.stream(positionsToBreak)
                .map(PackedBlockPos::toBlockPos)
                .toArray(BlockPos[]::new);
    }
}
