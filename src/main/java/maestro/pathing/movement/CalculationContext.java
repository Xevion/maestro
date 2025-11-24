package maestro.pathing.movement;

import static maestro.api.pathing.movement.ActionCosts.COST_INF;

import java.util.ArrayList;
import java.util.List;
import maestro.Maestro;
import maestro.api.IMaestro;
import maestro.api.pathing.movement.ActionCosts;
import maestro.cache.WorldData;
import maestro.pathing.precompute.PrecomputedData;
import maestro.utils.BlockStateInterface;
import maestro.utils.ToolSet;
import maestro.utils.pathing.BetterWorldBorder;
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
    public final IMaestro maestro;
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
    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData;

    public CalculationContext(IMaestro maestro) {
        this(maestro, false);
    }

    public CalculationContext(IMaestro maestro, boolean forUseOnAnotherThread) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.maestro = maestro;
        LocalPlayer player = maestro.getPlayerContext().player();
        this.world = maestro.getPlayerContext().world();
        this.worldData = (WorldData) maestro.getPlayerContext().worldData();
        this.bsi = new BlockStateInterface(maestro.getPlayerContext(), forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway =
                Maestro.settings().allowPlace.value
                        && ((Maestro) maestro).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket =
                Maestro.settings().allowWaterBucketFall.value
                        && Inventory.isHotbarSlot(
                                player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER))
                        && world.dimension() != Level.NETHER;
        this.canSprint =
                Maestro.settings().allowSprint.value && player.getFoodData().getFoodLevel() > 6;
        this.placeBlockCost = Maestro.settings().blockPlacementPenalty.value;
        this.allowBreak = Maestro.settings().allowBreak.value;
        this.allowBreakAnyway = new ArrayList<>(Maestro.settings().allowBreakAnyway.value);
        this.allowParkour = Maestro.settings().allowParkour.value;
        this.allowParkourPlace = Maestro.settings().allowParkourPlace.value;
        this.allowJumpAtBuildLimit = Maestro.settings().allowJumpAtBuildLimit.value;
        this.allowParkourAscend = Maestro.settings().allowParkourAscend.value;
        this.assumeWalkOnWater = Maestro.settings().assumeWalkOnWater.value;
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
        this.allowDiagonalDescend = Maestro.settings().allowDiagonalDescend.value;
        this.allowDiagonalAscend = Maestro.settings().allowDiagonalAscend.value;
        this.allowDownward = Maestro.settings().allowDownward.value;
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater = Maestro.settings().maxFallHeightNoWater.value;
        this.maxFallHeightBucket = Maestro.settings().maxFallHeightBucket.value;
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
        this.breakBlockAdditionalCost = Maestro.settings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient =
                Maestro.settings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = Maestro.settings().jumpPenalty.value;
        this.walkOnWaterOnePenalty = Maestro.settings().walkOnWaterOnePenalty.value;
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    public final IMaestro getMaestro() {
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
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return COST_INF;
        }
        if (!Maestro.settings().allowPlaceInFluidsSource.value
                && current.getFluidState().isSource()) {
            return COST_INF;
        }
        if (!Maestro.settings().allowPlaceInFluidsFlow.value
                && !current.getFluidState().isEmpty()
                && !current.getFluidState().isSource()) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
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
