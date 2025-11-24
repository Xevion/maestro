package maestro.process;

import java.util.*;
import java.util.stream.Collectors;
import maestro.Maestro;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.pathing.path.PathExecutor;
import maestro.utils.MaestroProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

public final class BackfillProcess extends MaestroProcessHelper {

    public HashMap<BlockPos, BlockState> blocksToReplace = new HashMap<>();

    public BackfillProcess(Maestro maestro) {
        super(maestro);
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (!Maestro.settings().backfill.value) {
            return false;
        }
        if (Maestro.settings().allowParkour.value) {
            logDirect("Backfill cannot be used with allowParkour true");
            Maestro.settings().backfill.value = false;
            return false;
        }
        for (BlockPos pos : new ArrayList<>(blocksToReplace.keySet())) {
            if (ctx.world().getChunk(pos) instanceof EmptyLevelChunk
                    || ctx.world().getBlockState(pos).getBlock() != Blocks.AIR) {
                blocksToReplace.remove(pos);
            }
        }
        amIBreakingABlockHMMMMMMM();
        maestro.getInputOverrideHandler().clearAllKeys();

        return !toFillIn().isEmpty();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!isSafeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        maestro.getInputOverrideHandler().clearAllKeys();
        for (BlockPos toPlace : toFillIn()) {
            MovementState fake = new MovementState();
            switch (MovementHelper.attemptToPlaceABlock(fake, maestro, toPlace, false, false)) {
                case NO_OPTION:
                    continue;
                case READY_TO_PLACE:
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                case ATTEMPTING:
                    // patience
                    maestro.getLookBehavior()
                            .updateTarget(fake.getTarget().getRotation().get(), true);
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                default:
                    throw new IllegalStateException();
            }
        }
        return new PathingCommand(null, PathingCommandType.DEFER); // cede to other process
    }

    private void amIBreakingABlockHMMMMMMM() {
        if (ctx.getSelectedBlock().isEmpty() || !maestro.getPathingBehavior().isPathing()) {
            return;
        }
        blocksToReplace.put(
                ctx.getSelectedBlock().get(),
                ctx.world().getBlockState(ctx.getSelectedBlock().get()));
    }

    public List<BlockPos> toFillIn() {
        return blocksToReplace.keySet().stream()
                .filter(pos -> ctx.world().getBlockState(pos).getBlock() == Blocks.AIR)
                .filter(
                        pos ->
                                maestro.getBuilderProcess()
                                        .placementPlausible(pos, Blocks.DIRT.defaultBlockState()))
                .filter(pos -> !partOfCurrentMovement(pos))
                .sorted(Comparator.<BlockPos>comparingDouble(ctx.playerFeet()::distSqr).reversed())
                .collect(Collectors.toList());
    }

    private boolean partOfCurrentMovement(BlockPos pos) {
        PathExecutor exec = maestro.getPathingBehavior().getCurrent();
        if (exec == null || exec.finished() || exec.failed()) {
            return false;
        }
        Movement movement = (Movement) exec.getPath().movements().get(exec.getPosition());
        return Arrays.asList(movement.toBreakAll()).contains(pos);
    }

    @Override
    public void onLostControl() {
        if (blocksToReplace != null && !blocksToReplace.isEmpty()) {
            blocksToReplace.clear();
        }
    }

    @Override
    public String displayName0() {
        return "Backfill";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 5;
    }
}
