package maestro.behavior;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import java.util.Set;
import maestro.Agent;
import maestro.api.cache.IWaypoint;
import maestro.api.cache.Waypoint;
import maestro.api.event.events.BlockInteractEvent;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.Helper;
import maestro.utils.BlockStateInterface;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public class WaypointBehavior extends Behavior {

    public WaypointBehavior(Agent maestro) {
        super(maestro);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        if (!Agent.settings().doBedWaypoints.value) return;
        if (event.getType() == BlockInteractEvent.Type.USE) {
            BetterBlockPos pos = BetterBlockPos.from(event.getPos());
            BlockState state = BlockStateInterface.get(ctx, pos);
            if (state.getBlock() instanceof BedBlock) {
                if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    pos = pos.relative(state.getValue(BedBlock.FACING));
                }
                Set<IWaypoint> waypoints =
                        maestro.getWorldProvider()
                                .getCurrentWorld()
                                .getWaypoints()
                                .getByTag(IWaypoint.Tag.BED);
                boolean exists =
                        waypoints.stream().map(IWaypoint::getLocation).anyMatch(pos::equals);
                if (!exists) {
                    maestro.getWorldProvider()
                            .getCurrentWorld()
                            .getWaypoints()
                            .addWaypoint(new Waypoint("bed", Waypoint.Tag.BED, pos));
                }
            }
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!Agent.settings().doDeathWaypoints.value) return;
        Waypoint deathWaypoint = new Waypoint("death", Waypoint.Tag.DEATH, ctx.playerFeet());
        maestro.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(deathWaypoint);
        MutableComponent component = Component.literal("Death position saved.");
        component.setStyle(
                component
                        .getStyle()
                        .withColor(ChatFormatting.WHITE)
                        .withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to goto death")))
                        .withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        String.format(
                                                "%s%s goto %s @ %d",
                                                FORCE_COMMAND_PREFIX,
                                                "wp",
                                                deathWaypoint.getTag().getName(),
                                                deathWaypoint.getCreationTimestamp()))));
        Helper.HELPER.logDirect(component);
    }
}
