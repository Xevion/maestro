package maestro.behavior;

import java.util.Set;
import maestro.Agent;
import maestro.api.cache.IWaypoint;
import maestro.api.cache.Waypoint;
import maestro.api.event.events.BlockInteractEvent;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
import maestro.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.slf4j.Logger;

public class WaypointBehavior extends Behavior {
    private static final Logger log = MaestroLogger.get("waypoint");

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
        log.atInfo()
                .addKeyValue("waypoint_type", "death")
                .addKeyValue("position", ctx.playerFeet())
                .log("Death position saved");
        // TODO: Clickable component removed - will be restored with ChatMessenger abstraction
    }
}
