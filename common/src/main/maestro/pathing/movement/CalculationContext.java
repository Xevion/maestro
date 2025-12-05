package maestro.pathing.movement;

import java.util.ArrayList;
import java.util.List;
import maestro.Agent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.cache.WorldData;
import maestro.pathing.BetterWorldBorder;
import maestro.pathing.BlockStateInterface;
import maestro.pathing.precompute.PrecomputedData;
import maestro.pathing.recovery.MovementFailureMemory;
import maestro.task.ToolSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CalculationContext {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);

    public final boolean safeForThreadedUse;
    public final Agent maestro;
    public final Level world;
    public final WorldData worldData;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    public final List<Block> allowBreakAnyway;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAtBuildLimit;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public boolean allowFallIntoLava;
    public final int frostWalker;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    public int minFallHeight;
    public int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final boolean allowSwimming;
    public final int minSwimmingDepth;
    public final boolean allowDiagonalSwimming;
    public final BetterWorldBorder worldBorder;
    public final boolean allowTeleport;
    public final int teleportGenerationSparsity;
    public final int teleportMinDistance;
    public final int teleportMaxDistance;
    public final double teleportCostMultiplier;
    public final MovementFailureMemory failureMemory;

    public final PrecomputedData precomputedData;

    public CalculationContext(Agent maestro) {
        this(maestro, false);
    }

    public CalculationContext(Agent maestro, boolean forUseOnAnotherThread) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.maestro = maestro;
        LocalPlayer player = maestro.getPlayerContext().player();
        this.world = maestro.getPlayerContext().world();
        this.worldData = (WorldData) maestro.getPlayerContext().worldData();
        this.bsi = new BlockStateInterface(maestro.getPlayerContext(), forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway =
                Agent.getPrimaryAgent().getSettings().allowPlace.value
                        && ((Agent) maestro).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket =
                Agent.getPrimaryAgent().getSettings().allowWaterBucketFall.value
                        && Inventory.isHotbarSlot(
                                player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER))
                        && world.dimension() != Level.NETHER;
        this.canSprint =
                Agent.getPrimaryAgent().getSettings().allowSprint.value
                        && player.getFoodData().getFoodLevel() > 6;
        this.placeBlockCost = Agent.getPrimaryAgent().getSettings().blockPlacementPenalty.value;
        this.allowBreak = Agent.getPrimaryAgent().getSettings().allowBreak.value;
        this.allowBreakAnyway =
                new ArrayList<>(Agent.getPrimaryAgent().getSettings().allowBreakAnyway.value);
        this.allowParkour = Agent.getPrimaryAgent().getSettings().allowParkour.value;
        this.allowParkourPlace = Agent.getPrimaryAgent().getSettings().allowParkourPlace.value;
        this.allowJumpAtBuildLimit =
                Agent.getPrimaryAgent().getSettings().allowJumpAtBuildLimit.value;
        this.allowParkourAscend = Agent.getPrimaryAgent().getSettings().allowParkourAscend.value;
        this.assumeWalkOnWater = Agent.getPrimaryAgent().getSettings().assumeWalkOnWater.value;
        this.allowFallIntoLava = false; // Super secret internal setting for ElytraBehavior
        // todo: technically there can now be datapack enchants that replace blocks with any other
        // at any range
        int frostWalkerLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments =
                    maestro.getPlayerContext().player().getItemBySlot(slot).getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                if (enchant.is(Enchantments.FROST_WALKER)) {
                    frostWalkerLevel = itemEnchantments.getLevel(enchant);
                }
            }
        }
        this.frostWalker = frostWalkerLevel;
        this.allowDiagonalDescend =
                Agent.getPrimaryAgent().getSettings().allowDiagonalDescend.value;
        this.allowDiagonalAscend = Agent.getPrimaryAgent().getSettings().allowDiagonalAscend.value;
        this.allowDownward = Agent.getPrimaryAgent().getSettings().allowDownward.value;
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater =
                Agent.getPrimaryAgent().getSettings().maxFallHeightNoWater.value;
        this.maxFallHeightBucket = Agent.getPrimaryAgent().getSettings().maxFallHeightBucket.value;
        float waterSpeedMultiplier = 1.0f;
        OUTER:
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments =
                    maestro.getPlayerContext().player().getItemBySlot(slot).getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                List<EnchantmentAttributeEffect> effects =
                        enchant.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES);
                for (EnchantmentAttributeEffect effect : effects) {
                    if (effect.attribute()
                            .is(Attributes.WATER_MOVEMENT_EFFICIENCY.unwrapKey().get())) {
                        waterSpeedMultiplier =
                                effect.amount().calculate(itemEnchantments.getLevel(enchant));
                        break OUTER;
                    }
                }
            }
        }
        this.waterWalkSpeed =
                ActionCosts.WALK_ONE_IN_WATER_COST * (1 - waterSpeedMultiplier)
                        + ActionCosts.WALK_ONE_BLOCK_COST * waterSpeedMultiplier;
        this.breakBlockAdditionalCost =
                Agent.getPrimaryAgent().getSettings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient =
                Agent.getPrimaryAgent().getSettings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = Agent.getPrimaryAgent().getSettings().jumpPenalty.value;
        this.walkOnWaterOnePenalty =
                Agent.getPrimaryAgent().getSettings().walkOnWaterOnePenalty.value;
        this.allowSwimming =
                Agent.getPrimaryAgent().getSettings().allowSwimming.value
                        && Agent.getPrimaryAgent().getSettings().enhancedSwimming.value;
        this.minSwimmingDepth = Agent.getPrimaryAgent().getSettings().minSwimmingDepth.value;
        this.allowDiagonalSwimming =
                Agent.getPrimaryAgent().getSettings().allowDiagonalSwimming.value;
        this.allowTeleport = Agent.getPrimaryAgent().getSettings().allowTeleport.value;
        this.teleportGenerationSparsity =
                Agent.getPrimaryAgent().getSettings().teleportGenerationSparsity.value;
        this.teleportMinDistance = Agent.getPrimaryAgent().getSettings().teleportMinDistance.value;
        this.teleportMaxDistance = Agent.getPrimaryAgent().getSettings().teleportMaxDistance.value;
        this.teleportCostMultiplier =
                Agent.getPrimaryAgent().getSettings().teleportCostMultiplier.value;
        this.failureMemory = ((Agent) maestro).getPathingBehavior().failureMemory;
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    public final Agent getMaestro() {
        return maestro;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, BlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return ActionCosts.COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return ActionCosts.COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return ActionCosts.COST_INF;
        }
        if (!Agent.getPrimaryAgent().getSettings().allowPlaceInFluidsSource.value
                && current.getFluidState().isSource()) {
            return ActionCosts.COST_INF;
        }
        if (!Agent.getPrimaryAgent().getSettings().allowPlaceInFluidsFlow.value
                && !current.getFluidState().isEmpty()
                && !current.getFluidState().isSource()) {
            return ActionCosts.COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return ActionCosts.COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return ActionCosts.COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    public boolean isPossiblyProtected(int x, int y, int z) {
        // TODO more protection logic here; see #220
        return false;
    }
}
