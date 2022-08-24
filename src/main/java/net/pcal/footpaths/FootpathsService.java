package net.pcal.footpaths;

import com.google.common.collect.ImmutableSet;
import com.google.common.math.DoubleMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.pcal.footpaths.FootpathsRuntimeConfig.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static java.util.Objects.requireNonNull;


/**
 * Central singleton service.
 */
public class FootpathsService {

    // ===================================================================================
    // Constants

    public static final String LOGGER_NAME = "footpaths";
    public static final String LOG_PREFIX = "[Footpaths] ";

    // ===================================================================================
    // Singleton

    private static final class SingletonHolder {
        private static final FootpathsService INSTANCE;

        static {
            INSTANCE = new FootpathsService();
        }
    }

    public static FootpathsService getInstance() {
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

    FootpathsService() {
        this.stepCounts = new HashMap<>();
    }

    public void configure(FootpathsRuntimeConfig config) {
        this.config = requireNonNull(config);
    }

    // ===================================================================================
    // Fields

    private final Logger logger = LogManager.getLogger(LOGGER_NAME);
    private FootpathsRuntimeConfig config;
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
        final BlockPos pos = new BlockPos(x, y, z).down(1);
        final World world = entity.getWorld();
        final BlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        final Identifier blockId = Registry.BLOCK.getId(block);

        // Get the rules that might apply to that block.  This just lets us avoid processing
        // rules if they don't apply to the block (which is most of the time).
        final List<Rule> blockRules = this.config.getRuleListForBlock(blockId);
        if (blockRules == null) return;

        logger.debug(() -> "checking " + blockId);

        final Set<Identifier> bootInfo = getBootInfo(entity);
        for (Rule rule : blockRules) {
            if (!blockId.equals(rule.blockId())) continue;
            if (!entityRuleSet.contains(rule)) continue;
            if (!rule.onlyIfBootIds().isEmpty()) {
                if (!matchesAny(bootInfo, rule.onlyIfBootIds())) continue;
            }
            if (!rule.skipIfBootIds().isEmpty()) {
                if (matchesAny(bootInfo, rule.skipIfBootIds())) continue;
            }
            triggerRule(rule, world, pos, block);
            return;
        }
    }

    private static boolean matchesAny(Set<Identifier> bootInfo, List<Set<Identifier>> setList) {
        for (final Set<Identifier> set : setList) {
            if (bootInfo.containsAll(set)) return true;
        }
        return false;
    }


    private static final Set<Identifier> BAREFOOT = ImmutableSet.of(new Identifier("minecraft:none"));

    /**
     * Return info for the boots the player is wearing.
     */
    private static Set getBootInfo(Entity entity) {
        for (ItemStack armor : entity.getArmorItems()) {
            final Set<Identifier> bootInfo = getBootInfo(armor);
            if (bootInfo != null) return bootInfo;
        }
        return BAREFOOT;
    }

        /**
         * Return info about the footwear in the given stack, or null if it isn't footwear.
         */
    private static Set<Identifier> getBootInfo(ItemStack stack) {
        if (!(stack.getItem() instanceof final ArmorItem armor)) return null;
        if (armor.getSlotType() != EquipmentSlot.FEET) return null;
        final Identifier bootId = Registry.ITEM.getId(stack.getItem());
        final NbtList enchants = stack.getEnchantments();
        if (enchants == null || enchants.isEmpty()) {
            return ImmutableSet.of(bootId);
        } else {
            final Set<Identifier> bootInfo = new HashSet<>();
            bootInfo.add(bootId);
            for (final NbtElement enchant : enchants) {
                if (enchant instanceof final NbtCompound compound) {
                    final String id = requireNonNull(compound.getString("id"));
                    // final int lvl = compound.getInt("lvl"); TODO someday?
                    bootInfo.add(new Identifier(id));
                }
            }
            return bootInfo;
        }
    }

    private void triggerRule(Rule rule, World world, BlockPos pos, Block block) {
        final BlockHistory bh = this.stepCounts.get(pos);
        final int blockStepCount;
        if (bh == null) {
            blockStepCount = 1;
        } else {
            if (rule.timeoutTicks() > 0 && (world.getTime() - bh.lastStepTimestamp) > rule.timeoutTicks()) {
                logger.debug(() -> "step timeout " + block + " " + bh);
                blockStepCount = 1;
                bh.stepCount = 1;
            } else {
                logger.debug(() -> "stepCount++ " + block + " " + bh);
                bh.stepCount++;
                blockStepCount = bh.stepCount;
            }
            bh.lastStepTimestamp = world.getTime();
        }
        if (blockStepCount >= rule.stepCount()) {
            logger.debug(() -> "changed! " + block + " " + bh);
            final Identifier nextId = rule.nextId();
            world.setBlockState(pos, Registry.BLOCK.get(nextId).getDefaultState());
            if (bh != null) this.stepCounts.remove(pos);
        } else {
            if (bh == null) {
                this.stepCounts.put(pos, new BlockHistory(blockStepCount, world.getTime()));
            }
        }
    }
}
