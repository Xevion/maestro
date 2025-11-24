package maestro.process;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import maestro.Agent;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalNear;
import maestro.api.process.IAttackProcess;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.api.utils.Rotation;
import maestro.api.utils.RotationUtils;
import maestro.utils.MaestroProcessHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attack entities matching a predicate. Paths to targets and attacks them until no matching
 * entities remain in range.
 */
public final class AttackProcess extends MaestroProcessHelper implements IAttackProcess {

    private static final double MELEE_RANGE = 4.5;

    private Predicate<Entity> filter;
    private List<Entity> targets;
    private int lastAttackTick = -1;

    public AttackProcess(Agent maestro) {
        super(maestro);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        targets = scanTargets();

        if (targets.isEmpty()) {
            // No targets remaining - cancel attack
            onLostControl();
            return null;
        }

        Entity primaryTarget = targets.get(0);

        // Check if in melee range (measure to nearest point on hitbox, not center)
        AABB hitbox = primaryTarget.getBoundingBox();
        double closestX = Mth.clamp(ctx.player().getX(), hitbox.minX, hitbox.maxX);
        double closestY = Mth.clamp(ctx.player().getY(), hitbox.minY, hitbox.maxY);
        double closestZ = Mth.clamp(ctx.player().getZ(), hitbox.minZ, hitbox.maxZ);
        double distanceSq = ctx.player().distanceToSqr(closestX, closestY, closestZ);

        if (distanceSq <= MELEE_RANGE * MELEE_RANGE) {
            // In range - attack if cooldown ready
            if (canAttack()) {
                attack(primaryTarget);
            }
            // Stay in place while attacking
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        } else {
            // Path to target - stay at comfortable attack range
            Goal goal = new GoalNear(primaryTarget.blockPosition(), 3);
            return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }
    }

    private List<Entity> scanTargets() {
        return ctx.entitiesStream()
                .filter(this::isValidTarget)
                .filter(filter)
                .sorted(Comparator.comparingDouble(e -> ctx.player().distanceToSqr(e)))
                .collect(Collectors.toList());
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.equals(ctx.player())) {
            return false;
        }
        if (!entity.isAlive()) {
            return false;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return true;
    }

    private boolean canAttack() {
        // Check if attack cooldown is at full charge for maximum damage
        float attackStrength = ctx.player().getAttackStrengthScale(0.5F);

        // Only attack when fully charged (1.0F) to deal maximum damage and enable critical hits
        // getAttackStrengthScale() automatically accounts for weapon-specific attack speeds
        if (attackStrength < 1.0F) {
            return false;
        }

        // Track last attack to prevent redundant packets when cooldown isn't ready
        int currentTick = ctx.minecraft().player.tickCount;
        if (lastAttackTick == currentTick) {
            return false;
        }

        return true;
    }

    private void attack(Entity target) {
        // Calculate rotation to target (aim for center of entity)
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);
        Rotation targetRot =
                RotationUtils.calcRotationFromVec3d(
                        ctx.player().getEyePosition(1.0F), targetPos, ctx.playerRotations());

        // Update look behavior to rotate towards target (for visual appearance)
        maestro.getLookBehavior().updateTarget(targetRot, true);

        // Attack and track the tick to prevent redundant attacks
        ctx.minecraft().gameMode.attack(ctx.player(), target);
        ctx.player().swing(InteractionHand.MAIN_HAND);

        // Record attack and reset cooldown timer
        lastAttackTick = ctx.minecraft().player.tickCount;
        ctx.player().resetAttackStrengthTicker();
    }

    @Override
    public boolean isActive() {
        if (filter == null) {
            return false;
        }
        targets = scanTargets();
        return !targets.isEmpty();
    }

    @Override
    public void onLostControl() {
        filter = null;
        targets = null;
        lastAttackTick = -1;
    }

    @Override
    public String displayName0() {
        if (targets == null || targets.isEmpty()) {
            return "Attacking (no targets)";
        }
        return "Attacking " + targets.size() + " target(s)";
    }

    @Override
    public void attack(Predicate<Entity> filter) {
        this.filter = filter;
    }

    @Override
    public List<Entity> getTargets() {
        return targets;
    }
}
