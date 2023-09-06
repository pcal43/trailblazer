package net.pcal.trailblazer;

import com.google.common.collect.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Runtime representation of configuration. FIXME should allow more than one RBC per block bootId
 */
@SuppressWarnings("ClassCanBeRecord")
class TrailblazerRuntimeConfig {

    private final List<Rule> rules;
    private final ListMultimap<Identifier, Rule> rulesPerBlock = ArrayListMultimap.create();
    private final SetMultimap<Identifier, Rule> rulesPerEntity = HashMultimap.create();
    private final SetMultimap<SpawnGroup, Rule> rulesPerSpawnGroup = HashMultimap.create();
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

    List<Rule> getRuleListForBlock(Identifier blockId) {
        return this.rulesPerBlock.get(blockId);
    }

    Set<Rule> getRulesForEntity(Entity entity) {
        final Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (this.rulesPerSpawnGroup.isEmpty()) {
            return this.rulesPerEntity.get(entityId);
        } else {
            final SpawnGroup spawnGroup = entity.getType().getSpawnGroup();
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
            Identifier blockId,
            Identifier nextId,
            int stepCount,
            int timeoutTicks,
            Set<Identifier> entityIds,
            Set<SpawnGroup> spawnGroups,
            List<Set<Identifier>> skipIfBoots,
            List<Set<Identifier>> onlyIfBoots
    ) {

        Rule(
                String name,
                Identifier blockId,
                Identifier nextId,
                int stepCount,
                int timeoutTicks,
                Set<Identifier> entityIds,
                Set<SpawnGroup> spawnGroups,
                List<Set<Identifier>> skipIfBoots,
                List<Set<Identifier>> onlyIfBoots) {
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
