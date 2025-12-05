package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalStrictDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class TunnelCommand extends Command {

    public TunnelCommand(Agent maestro) {
        super(maestro, "tunnel");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(3);
        if (args.hasExactly(3)) {
            boolean cont = true;
            int height = Integer.parseInt(args.getArgs().get(0).getValue());
            int width = Integer.parseInt(args.getArgs().get(1).getValue());
            int depth = Integer.parseInt(args.getArgs().get(2).getValue());

            if (width < 1 || height < 2 || depth < 1 || height > ctx.world().getMaxY()) {
                log.atWarn()
                        .log(
                                "Width and depth must at least be 1 block; Height must at least"
                                        + " be 2 blocks, and cannot be greater than the build"
                                        + " limit.");
                cont = false;
            }

            if (cont) {
                height--;
                width--;
                BlockPos corner1;
                BlockPos corner2;
                Direction enumFacing = ctx.player().getDirection();
                int addition = ((width % 2 == 0) ? 0 : 1);
                corner2 =
                        switch (enumFacing) {
                            case EAST -> {
                                corner1 =
                                        new BlockPos(
                                                ctx.playerFeet().getX(),
                                                ctx.playerFeet().getY(),
                                                ctx.playerFeet().getZ() - width / 2);
                                yield new BlockPos(
                                        ctx.playerFeet().getX() + depth,
                                        ctx.playerFeet().getY() + height,
                                        ctx.playerFeet().getZ() + width / 2 + addition);
                            }
                            case WEST -> {
                                corner1 =
                                        new BlockPos(
                                                ctx.playerFeet().getX(),
                                                ctx.playerFeet().getY(),
                                                ctx.playerFeet().getZ() + width / 2 + addition);
                                yield new BlockPos(
                                        ctx.playerFeet().getX() - depth,
                                        ctx.playerFeet().getY() + height,
                                        ctx.playerFeet().getZ() - width / 2);
                            }
                            case NORTH -> {
                                corner1 =
                                        new BlockPos(
                                                ctx.playerFeet().getX() - width / 2,
                                                ctx.playerFeet().getY(),
                                                ctx.playerFeet().getZ());
                                yield new BlockPos(
                                        ctx.playerFeet().getX() + width / 2 + addition,
                                        ctx.playerFeet().getY() + height,
                                        ctx.playerFeet().getZ() - depth);
                            }
                            case SOUTH -> {
                                corner1 =
                                        new BlockPos(
                                                ctx.playerFeet().getX() + width / 2 + addition,
                                                ctx.playerFeet().getY(),
                                                ctx.playerFeet().getZ());
                                yield new BlockPos(
                                        ctx.playerFeet().getX() - width / 2,
                                        ctx.playerFeet().getY() + height,
                                        ctx.playerFeet().getZ() + depth);
                            }
                            default ->
                                    throw new IllegalStateException(
                                            "Unexpected value: " + enumFacing);
                        };
                log.atInfo()
                        .addKeyValue("height", height + 1)
                        .addKeyValue("width", width + 1)
                        .addKeyValue("depth", depth)
                        .log("Creating tunnel");
                maestro.getBuilderTask().clearArea(corner1, corner2);
            }
        } else {
            Goal goal =
                    new GoalStrictDirection(
                            ctx.playerFeet().toBlockPos(), ctx.player().getDirection());
            maestro.getCustomGoalTask().setGoalAndPath(goal);
            log.atInfo().addKeyValue("goal", goal).log("Goal set");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Set a goal to tunnel in your current direction";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The tunnel command sets a goal that tells Maestro to mine completely straight in"
                        + " the direction that you're facing.",
                "",
                "Usage:",
                "> tunnel - No arguments, mines in a 1x2 radius.",
                "> tunnel <height> <width> <depth> - Tunnels in a user defined height, width and"
                        + " depth.");
    }
}
