package net.pcal.trailblazer;

import com.google.common.collect.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;

import static java.util.Objects.requireNonNull;

/**
 * Runtime representation of configuration. FIXME should allow more than one RBC per block bootId
 */
@SuppressWarnings("ClassCanBeRecord")
class TrailblazerRuntimeConfig {

    private final List<Rule> rules;
    private final ListMultimap<ResourceLocation, Rule> rulesPerBlock = ArrayListMultimap.create();
    private final SetMultimap<ResourceLocation, Rule> rulesPerEntity = HashMultimap.create();
    private final SetMultimap<MobCategory, Rule> rulesPerSpawnGroup = HashMultimap.create();
    private final int stepCacheSize;

    TrailblazerRuntimeConfig(List<Rule> rules, int stepCacheSize) {
        this.rules = requireNonNull(rules);
        this.stepCacheSize = stepCacheSize;
        for (final Rule rule : rules) {
            this.rulesPerBlock.put(rule.blockId(), rule);
            rule.entityIds().forEach(id -> this.rulesPerEntity.put(id, rule));
            rule.spawnGroups().forEach(group -> this.rulesPerSpawnGroup.put(group, rule));
        }
    }

    int getStepCacheSize() {
        return this.stepCacheSize;
    }

    List<Rule> getRuleListForBlock(ResourceLocation blockId) {
        return this.rulesPerBlock.get(blockId);
    }

    Set<Rule> getRulesForEntity(Entity entity) {
        final ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (this.rulesPerSpawnGroup.isEmpty()) {
            return this.rulesPerEntity.get(entityId);
        } else {
            final MobCategory spawnGroup = entity.getType().getCategory();
            final Set<Rule> spawnGroupRules = this.rulesPerSpawnGroup.get(spawnGroup);
            if (spawnGroupRules.isEmpty()) {
                return this.rulesPerEntity.get(entityId);
            } else {
                // FIXME? I suppose we could also be build up a cache of these things
                return Sets.union(this.rulesPerEntity.get(entityId), spawnGroupRules);
            }
        }
    }

    record Rule(
            String name,
            ResourceLocation blockId,
            ResourceLocation nextId,
            int stepCount,
            int timeoutTicks,
            Set<ResourceLocation> entityIds,
            Set<MobCategory> spawnGroups,
            List<Set<ResourceLocation>> skipIfBoots,
            List<Set<ResourceLocation>> onlyIfBoots
    ) {

        Rule(
                String name,
                ResourceLocation blockId,
                ResourceLocation nextId,
                int stepCount,
                int timeoutTicks,
                Set<ResourceLocation> entityIds,
                Set<MobCategory> spawnGroups,
                List<Set<ResourceLocation>> skipIfBoots,
                List<Set<ResourceLocation>> onlyIfBoots) {
            this.name = name != null ? name : "unnamed";
            this.blockId = requireNonNull(blockId);
            this.nextId = requireNonNull(nextId);
            this.stepCount = stepCount;
            this.timeoutTicks = timeoutTicks;
            this.entityIds = emptySetIfNull(entityIds);
            this.spawnGroups = emptySetIfNull(spawnGroups);
            this.skipIfBoots = emptyListIfNull(skipIfBoots);
            this.onlyIfBoots = emptyListIfNull(onlyIfBoots);
            if (!this.skipIfBoots.isEmpty() && !this.onlyIfBoots.isEmpty()) {
                throw new RuntimeException("Rules can't set both skipIfBoots and onlyIfBoots");
            }
        }

        private static <T> Set<T> emptySetIfNull(Set<T> set) {
            return set == null ? Collections.emptySet() : set;
        }

        private static <T> List<T> emptyListIfNull(List<T> list) {
            return list == null ? Collections.emptyList() : list;
        }
    }
}
