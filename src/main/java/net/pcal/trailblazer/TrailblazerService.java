package net.pcal.trailblazer;

import com.google.common.collect.ImmutableSet;
import com.google.common.math.DoubleMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.pcal.trailblazer.TrailblazerRuntimeConfig.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static java.util.Objects.requireNonNull;


/**
 * Central singleton service.
 */
public class TrailblazerService {

    // ===================================================================================
    // Constants

    public static final String LOGGER_NAME = "trailblazer";
    public static final String LOG_PREFIX = "[Trailblazer] ";

    // ===================================================================================
    // Singleton

    private static final class SingletonHolder {
        private static final TrailblazerService INSTANCE;

        static {
            INSTANCE = new TrailblazerService();
        }
    }

    public static TrailblazerService getInstance() {
        return SingletonHolder.INSTANCE;
    }

    static final class BlockHistory {
        BlockHistory(int stepCount, long lastStepTimestamp) {
            this.stepCount = stepCount;
            this.lastStepTimestamp = lastStepTimestamp;
        }

        int stepCount;
        long lastStepTimestamp;

        @Override
        public String toString() {
            return "stepCount: " + this.stepCount + " lastStepTimestamp: " + this.lastStepTimestamp;
        }
    }

    // ===================================================================================
    // Constructors

    TrailblazerService() {
        this.stepCounts = new LinkedHashMap<>(500, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry eldest) {
                if (config == null) {
                    logger.warn("config not set.  something is very wrong");
                    return false;
                } else {
                    return size() > config.getStepCacheSize();
                }
            }
        };
    }

    public void configure(TrailblazerRuntimeConfig config) {
        this.config = requireNonNull(config);
    }

    // ===================================================================================
    // Fields

    private final Logger logger = LogManager.getLogger(LOGGER_NAME);
    private TrailblazerRuntimeConfig config;
    private final Map<BlockPos, BlockHistory> stepCounts;

    /**
     * This will be called whenever an entity moves to a new block.
     */
    public void entitySteppingOnBlock(Entity entity, double x, double y, double z) {
        final Set<Rule> entityRuleSet = this.config.getRulesForEntity(entity);
        if (entityRuleSet == null || entityRuleSet.isEmpty()) {
            // Most mob movements presumably won't trigger a rule, so let's short-circuit
            // that case as quickly as possible.
            return;
        }
        if (!DoubleMath.isMathematicalInteger(entity.getY())) {
            // Ignore block changes for entities that aren't standing on the ground.
            // Mainly because the jumping players register extra blockPos changes
            // that I don't quite understand.
            return;
        }
        // Ok, figure out what block its standing on
        final BlockPos pos = BlockPos.containing(x, y, z).below(1);
        final Level world = entity.level();
        final BlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);

        // Get the rules that might apply to that block.  This just lets us avoid processing
        // rules if they don't apply to the block (which is most of the time).
        final List<Rule> blockRules = this.config.getRuleListForBlock(blockId);
        if (blockRules == null) return;

        logger.debug(() -> "checking " + blockId);

        final Set<ResourceLocation> bootInfo = getBootInfo(entity);
        for (Rule rule : blockRules) {
            if (!blockId.equals(rule.blockId())) continue;
            if (!entityRuleSet.contains(rule)) continue;
            if (!rule.onlyIfBoots().isEmpty()) {
                if (!matchesAny(bootInfo, rule.onlyIfBoots())) continue;
            }
            if (!rule.skipIfBoots().isEmpty()) {
                if (matchesAny(bootInfo, rule.skipIfBoots())) continue;
            }
            triggerRule(rule, world, pos, block);
            return;
        }
    }

    private static boolean matchesAny(Set<ResourceLocation> bootInfo, List<Set<ResourceLocation>> setList) {
        for (final Set<ResourceLocation> set : setList) {
            if (bootInfo.containsAll(set)) return true;
        }
        return false;
    }


    private static final Set<ResourceLocation> BAREFOOT = ImmutableSet.of(new ResourceLocation("minecraft:none"));

    /**
     * Return info for the boots the player is wearing.
     */
    private static Set getBootInfo(Entity entity) {
        for (ItemStack armor : entity.getArmorSlots()) {
            final Set<ResourceLocation> bootInfo = getBootInfo(armor);
            if (bootInfo != null) return bootInfo;
        }
        return BAREFOOT;
    }

        /**
         * Return info about the footwear in the given stack, or null if it isn't footwear.
         */
    private static Set<ResourceLocation> getBootInfo(ItemStack stack) {
        if (!(stack.getItem() instanceof final ArmorItem armor)) return null;
        if (armor.getEquipmentSlot() != EquipmentSlot.FEET) return null;
        final ResourceLocation bootId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final ListTag enchants = stack.getEnchantmentTags();
        if (enchants == null || enchants.isEmpty()) {
            return ImmutableSet.of(bootId);
        } else {
            final Set<ResourceLocation> bootInfo = new HashSet<>();
            bootInfo.add(bootId);
            for (final Tag enchant : enchants) {
                if (enchant instanceof final CompoundTag compound) {
                    final String id = requireNonNull(compound.getString("id"));
                    // final int lvl = compound.getInt("lvl"); TODO someday?
                    bootInfo.add(new ResourceLocation(id));
                }
            }
            return bootInfo;
        }
    }

    private void triggerRule(Rule rule, Level world, BlockPos pos, Block block) {
        final BlockHistory bh = this.stepCounts.get(pos);
        final int blockStepCount;
        if (bh == null) {
            blockStepCount = 1;
        } else {
            if (rule.timeoutTicks() > 0 && (world.getGameTime() - bh.lastStepTimestamp) > rule.timeoutTicks()) {
                logger.debug(() -> "step timeout " + block + " " + bh);
                blockStepCount = 1;
                bh.stepCount = 1;
            } else {
                logger.debug(() -> "stepCount++ " + block + " " + bh);
                bh.stepCount++;
                blockStepCount = bh.stepCount;
            }
            bh.lastStepTimestamp = world.getGameTime();
        }
        if (blockStepCount >= rule.stepCount()) {
            logger.debug(() -> "changed! " + block + " " + bh);
            final ResourceLocation nextId = rule.nextId();
            world.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(nextId).defaultBlockState());
            if (bh != null) this.stepCounts.remove(pos);
        } else {
            if (bh == null) {
                this.stepCounts.put(pos, new BlockHistory(blockStepCount, world.getGameTime()));
            }
        }
    }
}
