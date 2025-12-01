package maestro.process;

import static maestro.api.pathing.movement.ActionCosts.COST_INF;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.*;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.event.events.*;
import maestro.api.event.events.type.EventState;
import maestro.api.event.listener.AbstractGameEventListener;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.pathing.goals.GoalXZ;
import maestro.api.pathing.goals.GoalYLevel;
import maestro.api.pathing.movement.IMovement;
import maestro.api.pathing.path.IPathExecutor;
import maestro.api.process.IElytraProcess;
import maestro.api.process.IMaestroProcess;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.Rotation;
import maestro.api.utils.RotationUtils;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.movements.MovementFall;
import maestro.process.elytra.ElytraBehavior;
import maestro.process.elytra.NetherPathfinderContext;
import maestro.process.elytra.NullElytraProcess;
import maestro.utils.MaestroProcessHelper;
import maestro.utils.PathingCommandContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ElytraProcess extends MaestroProcessHelper
        implements IMaestroProcess, IElytraProcess, AbstractGameEventListener {
    private static final Logger log = MaestroLogger.get("path");

    public State state;
    private boolean goingToLandingSpot;
    private BetterBlockPos landingSpot;
    private boolean reachedGoal; // this basically just prevents potential notification spam
    private Goal goal;
    private ElytraBehavior behavior;
    private boolean predictingTerrain;

    @Override
    public void onLostControl() {
        this.state = State.START_FLYING; // TODO: null state?
        this.goingToLandingSpot = false;
        this.landingSpot = null;
        this.reachedGoal = false;
        this.goal = null;
        destroyBehaviorAsync();
    }

    private ElytraProcess(Agent maestro) {
        super(maestro);
        maestro.getGameEventHandler().registerEventListener(this);
    }

    public static IElytraProcess create(final Agent maestro) {
        return NetherPathfinderContext.isSupported()
                ? new ElytraProcess(maestro)
                : new NullElytraProcess(maestro);
    }

    @Override
    public boolean isActive() {
        return this.behavior != null;
    }

    @Override
    public void resetState() {
        BlockPos destination = this.currentDestination();
        this.onLostControl();
        if (destination != null) {
            this.pathTo(destination);
            this.repackChunks();
        }
    }

    private static final String AUTO_JUMP_FAILURE_MSG =
            "Failed to compute a walking path to a spot to jump off from. Consider starting from a"
                + " higher location, near an overhang. Or, you can disable elytraAutoJump and just"
                + " manually begin gliding.";

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        final long seedSetting = Agent.settings().elytraNetherSeed.value;
        if (seedSetting != this.behavior.context.getSeed()) {
            log.atInfo()
                    .addKeyValue("previous_seed", this.behavior.context.getSeed())
                    .addKeyValue("new_seed", seedSetting)
                    .log("Nether seed changed, recalculating path");
            this.resetState();
        }
        if (predictingTerrain != Agent.settings().elytraPredictTerrain.value) {
            log.atInfo()
                    .addKeyValue("setting", "elytraPredictTerrain")
                    .addKeyValue("new_value", Agent.settings().elytraPredictTerrain.value)
                    .log("Setting changed, recalculating path");
            predictingTerrain = Agent.settings().elytraPredictTerrain.value;
            this.resetState();
        }

        this.behavior.onTick();

        if (calcFailed) {
            onLostControl();
            log.atWarn().log("Failed to compute path for elytra auto-jump");
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        boolean safetyLanding = false;
        if (ctx.player().isFallFlying() && shouldLandForSafety()) {
            if (Agent.settings().elytraAllowEmergencyLand.value) {
                log.atInfo()
                        .addKeyValue("reason", "low_resources")
                        .log("Emergency landing initiated");
                safetyLanding = true;
            } else {
                log.atInfo()
                        .addKeyValue("setting", "elytraAllowEmergencyLand")
                        .addKeyValue("value", false)
                        .log("Low resources but continuing due to setting");
            }
        }
        if (ctx.player().isFallFlying()
                && this.state != State.LANDING
                && (this.behavior.pathManager.isComplete() || safetyLanding)) {
            final BetterBlockPos last = this.behavior.pathManager.path.getLast();
            if (last != null
                    && (ctx.player().position().distanceToSqr(last.getCenter()) < (48 * 48)
                            || safetyLanding)
                    && (!goingToLandingSpot || (safetyLanding && this.landingSpot == null))) {
                log.atInfo().log("Path complete, picking landing spot");
                BetterBlockPos landingSpot = findSafeLandingSpot(ctx.playerFeet());
                // if this fails we will just keep orbiting the last node until we run out of
                // rockets or the user intervenes
                if (landingSpot != null) {
                    this.pathTo0(landingSpot, true);
                    this.landingSpot = landingSpot;
                }
                this.goingToLandingSpot = true;
            }

            if (last != null && ctx.player().position().distanceToSqr(last.getCenter()) < 1) {
                if (Agent.settings().notificationOnPathComplete.value && !reachedGoal) {
                    logNotification("Pathing complete", false);
                }
                if (Agent.settings().disconnectOnArrival.value && !reachedGoal) {
                    // don't be active when the user logs back in
                    this.onLostControl();
                    ctx.world().disconnect();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                reachedGoal = true;

                // we are goingToLandingSpot, and we are in the last node of the path
                if (this.goingToLandingSpot) {
                    this.state = State.LANDING;
                    log.atInfo()
                            .addKeyValue("landing_spot", landingSpot)
                            .log("Above landing spot, initiating landing");
                }
            }
        }

        if (this.state == State.LANDING) {
            final BetterBlockPos endPos =
                    this.landingSpot != null
                            ? this.landingSpot
                            : behavior.pathManager.path.getLast();
            if (ctx.player().isFallFlying() && endPos != null) {
                Vec3 from = ctx.player().position();
                Vec3 to = new Vec3(((double) endPos.x) + 0.5, from.y, ((double) endPos.z) + 0.5);
                Rotation rotation =
                        RotationUtils.calcRotationFromVec3d(from, to, ctx.playerRotations());
                maestro.getLookBehavior()
                        .updateTarget(
                                new Rotation(rotation.getYaw(), 0),
                                false); // this will be overwritten, probably, by behavior tick

                if (ctx.player().position().y < endPos.y - LANDING_COLUMN_HEIGHT) {
                    log.atWarn()
                            .addKeyValue("landing_spot", endPos)
                            .log("Landing spot too low, selecting new spot");
                    landingSpotIsBad(endPos);
                }
            }
        }

        if (ctx.player().isFallFlying()) {
            behavior.landingMode = this.state == State.LANDING;
            this.goal = null;
            maestro.getInputOverrideHandler().clearAllKeys();
            behavior.tick();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        } else if (this.state == State.LANDING) {
            if (ctx.playerMotion().multiply(1, 0, 1).length() > 0.001) {
                log.atInfo().log("Landed, waiting for velocity to stabilize");
                maestro.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            log.atInfo().log("Elytra path complete");
            maestro.getInputOverrideHandler().clearAllKeys();
            this.onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (this.state == State.FLYING || this.state == State.START_FLYING) {
            this.state =
                    ctx.player().onGround() && Agent.settings().elytraAutoJump.value
                            ? State.LOCATE_JUMP
                            : State.START_FLYING;
        }

        if (this.state == State.LOCATE_JUMP) {
            if (shouldLandForSafety()) {
                log.atWarn()
                        .addKeyValue("reason", "insufficient_resources")
                        .log("Not taking off due to low elytra durability or fireworks");
                onLostControl();
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            if (this.goal == null) {
                this.goal = new GoalYLevel(31);
            }
            final IPathExecutor executor = maestro.getPathingBehavior().getCurrent();
            if (executor != null && executor.getPath().getGoal() == this.goal) {
                final IMovement fall =
                        executor.getPath().movements().stream()
                                .filter(movement -> movement instanceof MovementFall)
                                .findFirst()
                                .orElse(null);

                if (fall != null) {
                    final BetterBlockPos from =
                            new BetterBlockPos(
                                    (fall.getSrc().x + fall.getDest().x) / 2,
                                    (fall.getSrc().y + fall.getDest().y) / 2,
                                    (fall.getSrc().z + fall.getDest().z) / 2);
                    behavior.pathManager
                            .pathToDestination(from)
                            .whenComplete(
                                    (result, ex) -> {
                                        if (ex == null) {
                                            this.state = State.GET_TO_JUMP;
                                            return;
                                        }
                                        onLostControl();
                                    });
                    this.state = State.PAUSE;
                } else {
                    onLostControl();
                    log.atWarn().log("Failed to compute walking path to jump point");
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
            }
            return new PathingCommandContext(
                    this.goal,
                    PathingCommandType.SET_GOAL_AND_PAUSE,
                    new WalkOffCalculationContext(maestro));
        }

        // yucky
        if (this.state == State.PAUSE) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (this.state == State.GET_TO_JUMP) {
            final IPathExecutor executor = maestro.getPathingBehavior().getCurrent();
            // TODO 1.21.5: replace `ctx.player().getDeltaMovement().y < -0.377` with
            // `ctx.player().fallDistance > 1.0f`
            final boolean canStartFlying =
                    ctx.player().getDeltaMovement().y < -0.377
                            && !isSafeToCancel
                            && executor != null
                            && executor.getPath().movements().get(executor.getPosition())
                                    instanceof MovementFall;

            if (canStartFlying) {
                this.state = State.START_FLYING;
            } else {
                return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
            }
        }

        if (this.state == State.START_FLYING) {
            if (!isSafeToCancel) {
                // owned
                maestro.getPathingBehavior().secretInternalSegmentCancel();
            }
            maestro.getInputOverrideHandler().clearAllKeys();
            // TODO 1.21.5: replace `ctx.player().getDeltaMovement().y < -0.377` with
            // `ctx.player().fallDistance > 1.0f`
            if (ctx.player().getDeltaMovement().y < -0.377) {
                maestro.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            }
        }
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    public void landingSpotIsBad(BetterBlockPos endPos) {
        badLandingSpots.add(endPos);
        goingToLandingSpot = false;
        this.landingSpot = null;
        this.state = State.FLYING;
    }

    private void destroyBehaviorAsync() {
        ElytraBehavior behavior = this.behavior;
        if (behavior != null) {
            this.behavior = null;
            Agent.getExecutor().execute(behavior::destroy);
        }
    }

    @Override
    public double priority() {
        return 0; // higher priority than CustomGoalProcess
    }

    @Override
    public String displayName0() {
        return "Elytra - " + this.state.description;
    }

    @Override
    public void repackChunks() {
        if (this.behavior != null) {
            this.behavior.repackChunks();
        }
    }

    @Override
    public BlockPos currentDestination() {
        return this.behavior != null ? this.behavior.destination : null;
    }

    @Override
    public void pathTo(BlockPos destination) {
        this.pathTo0(destination, false);
    }

    private void pathTo0(BlockPos destination, boolean appendDestination) {
        if (ctx.player() == null || ctx.player().level().dimension() != Level.NETHER) {
            return;
        }
        this.onLostControl();
        this.predictingTerrain = Agent.settings().elytraPredictTerrain.value;
        this.behavior = new ElytraBehavior(this.maestro, this, destination, appendDestination);
        if (ctx.world() != null) {
            this.behavior.repackChunks();
        }
        this.behavior.pathTo();
    }

    @Override
    public void pathTo(Goal iGoal) {
        final int x;
        final int y;
        final int z;
        if (iGoal instanceof GoalXZ goal) {
            x = goal.getX();
            y = 64;
            z = goal.getZ();
        } else if (iGoal instanceof GoalBlock goal) {
            x = goal.x;
            y = goal.y;
            z = goal.z;
        } else {
            throw new IllegalArgumentException("The goal must be a GoalXZ or GoalBlock");
        }
        if (y <= 0 || y >= 128) {
            throw new IllegalArgumentException("The y of the goal is not between 0 and 128");
        }
        this.pathTo(new BlockPos(x, y, z));
    }

    private boolean shouldLandForSafety() {
        ItemStack chest = ctx.player().getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA
                || chest.getMaxDamage() - chest.getDamageValue()
                        < Agent.settings().elytraMinimumDurability.value) {
            // elytrabehavior replaces when durability <= minimumDurability, so if durability <
            // minimumDurability then we can reasonably assume that the elytra will soon be broken
            // without replacement
            return true;
        }

        NonNullList<ItemStack> inv = ctx.player().getInventory().items;
        int qty = 0;
        for (int i = 0; i < 36; i++) {
            if (ElytraBehavior.isFireworks(inv.get(i))) {
                qty += inv.get(i).getCount();
            }
        }
        return qty <= Agent.settings().elytraMinFireworksBeforeLanding.value;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isSafeToCancel() {
        return !this.isActive()
                || !(this.state == State.FLYING || this.state == State.START_FLYING);
    }

    public enum State {
        LOCATE_JUMP("Finding spot to jump off"),
        PAUSE("Waiting for elytra path"),
        GET_TO_JUMP("Walking to takeoff"),
        START_FLYING("Begin flying"),
        FLYING("Flying"),
        LANDING("Landing");

        public final String description;

        State(String desc) {
            this.description = desc;
        }
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (this.behavior != null) this.behavior.onRenderPass(event);
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        if (event.world != null && event.state == EventState.POST) {
            // Exiting the world, just destroy
            destroyBehaviorAsync();
        }
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (this.behavior != null) this.behavior.onChunkEvent(event);
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (this.behavior != null) this.behavior.onBlockChange(event);
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (this.behavior != null) this.behavior.onReceivePacket(event);
    }

    @Override
    public void onPostTick(TickEvent event) {
        IMaestroProcess procThisTick =
                maestro.getPathingControlManager().mostRecentInControl().orElse(null);
        if (this.behavior != null && procThisTick == this) this.behavior.onPostTick(event);
    }

    /** Custom calculation context which makes the player fall into lava */
    public static final class WalkOffCalculationContext extends CalculationContext {

        public WalkOffCalculationContext(IAgent maestro) {
            super(maestro, true);
            this.allowFallIntoLava = true;
            this.minFallHeight = 8;
            this.maxFallHeightNoWater = 10000;
        }

        @Override
        public double costOfPlacingAt(int x, int y, int z, BlockState current) {
            return COST_INF;
        }

        @Override
        public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
            return COST_INF;
        }

        @Override
        public double placeBucketCost() {
            return COST_INF;
        }
    }

    private static boolean isInBounds(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < 128;
    }

    private boolean isSafeBlock(Block block) {
        return block == Blocks.NETHERRACK
                || block == Blocks.GRAVEL
                || (block == Blocks.NETHER_BRICKS
                        && Agent.settings().elytraAllowLandOnNetherFortress.value);
    }

    private boolean isSafeBlock(BlockPos pos) {
        return isSafeBlock(ctx.world().getBlockState(pos).getBlock());
    }

    private boolean isAtEdge(BlockPos pos) {
        return !isSafeBlock(pos.north())
                || !isSafeBlock(pos.south())
                || !isSafeBlock(pos.east())
                || !isSafeBlock(pos.west())
                // corners
                || !isSafeBlock(pos.north().west())
                || !isSafeBlock(pos.north().east())
                || !isSafeBlock(pos.south().west())
                || !isSafeBlock(pos.south().east());
    }

    private boolean isColumnAir(BlockPos landingSpot, int minHeight) {
        BlockPos.MutableBlockPos mut =
                new BlockPos.MutableBlockPos(
                        landingSpot.getX(), landingSpot.getY(), landingSpot.getZ());
        final int maxY = mut.getY() + minHeight;
        for (int y = mut.getY() + 1; y <= maxY; y++) {
            mut.set(mut.getX(), y, mut.getZ());
            if (!(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAirBubble(BlockPos pos) {
        final int radius =
                4; // Half of the full width, rounded down, as we're counting blocks in each
        // direction from the center
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mut.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (!(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private BetterBlockPos checkLandingSpot(BlockPos pos, LongOpenHashSet checkedSpots) {
        BlockPos.MutableBlockPos mut =
                new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        while (mut.getY() >= 0) {
            if (checkedSpots.contains(mut.asLong())) {
                return null;
            }
            checkedSpots.add(mut.asLong());
            Block block = ctx.world().getBlockState(mut).getBlock();

            if (isSafeBlock(block)) {
                if (!isAtEdge(mut)) {
                    return new BetterBlockPos(mut);
                }
                return null;
            } else if (block != Blocks.AIR) {
                return null;
            }
            mut.set(mut.getX(), mut.getY() - 1, mut.getZ());
        }
        return null; // void
    }

    private static final int LANDING_COLUMN_HEIGHT = 15;
    private Set<BetterBlockPos> badLandingSpots = new HashSet<>();

    private BetterBlockPos findSafeLandingSpot(BetterBlockPos start) {
        Queue<BetterBlockPos> queue =
                new PriorityQueue<>(
                        Comparator.<BetterBlockPos>comparingInt(
                                        pos ->
                                                (pos.x - start.x) * (pos.x - start.x)
                                                        + (pos.z - start.z) * (pos.z - start.z))
                                .thenComparingInt(pos -> -pos.y));
        Set<BetterBlockPos> visited = new HashSet<>();
        LongOpenHashSet checkedPositions = new LongOpenHashSet();
        queue.add(start);

        while (!queue.isEmpty()) {
            BetterBlockPos pos = queue.poll();
            if (ctx.world().isLoaded(pos)
                    && isInBounds(pos)
                    && ctx.world().getBlockState(pos).getBlock() == Blocks.AIR) {
                BetterBlockPos actualLandingSpot = checkLandingSpot(pos, checkedPositions);
                if (actualLandingSpot != null
                        && isColumnAir(actualLandingSpot, LANDING_COLUMN_HEIGHT)
                        && hasAirBubble(actualLandingSpot.above(LANDING_COLUMN_HEIGHT))
                        && !badLandingSpots.contains(
                                actualLandingSpot.above(LANDING_COLUMN_HEIGHT))) {
                    return actualLandingSpot.above(LANDING_COLUMN_HEIGHT);
                }
                if (visited.add(pos.north())) queue.add(pos.north());
                if (visited.add(pos.east())) queue.add(pos.east());
                if (visited.add(pos.south())) queue.add(pos.south());
                if (visited.add(pos.west())) queue.add(pos.west());
                if (visited.add(pos.above())) queue.add(pos.above());
                if (visited.add(pos.below())) queue.add(pos.below());
            }
        }
        return null;
    }
}
