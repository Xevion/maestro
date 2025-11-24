package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.utils.BetterBlockPos;
import net.minecraft.world.level.block.AirBlock;

public class SurfaceCommand extends Command {

    protected SurfaceCommand(IAgent maestro) {
        super(maestro, "surface", "top");
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        final BetterBlockPos playerPos = ctx.playerFeet();
        final int surfaceLevel = ctx.world().getSeaLevel();
        final int worldHeight = ctx.world().getHeight();

        // Ensure this command will not run if you are above the surface level and the block above
        // you is air
        // As this would imply that you are already on the open surface
        if (playerPos.getY() > surfaceLevel
                && ctx.world().getBlockState(playerPos.above()).getBlock() instanceof AirBlock) {
            logDirect("Already at surface");
            return;
        }

        final int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

        for (int currentIteratedY = startingYPos;
                currentIteratedY < worldHeight;
                currentIteratedY++) {
            final BetterBlockPos newPos =
                    new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());

            if (!(ctx.world().getBlockState(newPos).getBlock() instanceof AirBlock)
                    && newPos.getY() > playerPos.getY()) {
                Goal goal = new GoalBlock(newPos.above());
                logDirect(String.format("Going to: %s", goal));
                maestro.getCustomGoalProcess().setGoalAndPath(goal);
                return;
            }
        }
        logDirect("No higher location found");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Used to get out of caves, mines, ...";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The surface/top command tells Maestro to head towards the closest surface-like"
                        + " area.",
                "",
                "This can be the surface or the highest available air space, depending on"
                        + " circumstances.",
                "",
                "Usage:",
                "> surface - Used to get out of caves, mines, ...",
                "> top - Used to get out of caves, mines, ...");
    }
}
