package net.pcal.footpaths;

import com.google.common.collect.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Runtime representation of configuration. FIXME should allow more than one RBC per block bootId
 */
@SuppressWarnings("ClassCanBeRecord")
class FootpathsRuntimeConfig {

    private final List<Rule> rules;
    private final ListMultimap<Identifier, Rule> rulesPerBlock = ArrayListMultimap.create();
    private final SetMultimap<Identifier, Rule> rulesPerEntity = HashMultimap.create();
    private final SetMultimap<SpawnGroup, Rule> rulesPerSpawnGroup = HashMultimap.create();

    FootpathsRuntimeConfig(List<Rule> rules) {
        this.rules = requireNonNull(rules);
        for (final Rule rule : rules) {
            this.rulesPerBlock.put(rule.blockId(), rule);
            rule.entityIds().forEach(id -> this.rulesPerEntity.put(id, rule));
            rule.spawnGroups().forEach(group -> this.rulesPerSpawnGroup.put(group, rule));
        }
    }

    List<Rule> getRuleListForBlock(Identifier blockId) {
        return this.rulesPerBlock.get(blockId);
    }

    Set<Rule> getRulesForEntity(Entity entity) {
        final Identifier entityId = Registry.ENTITY_TYPE.getId(entity.getType());
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
            List<Set<Identifier>> skipIfBootIds,
            List<Set<Identifier>> onlyIfBootIds
    ) {

        Rule(
                String name,
                Identifier blockId,
                Identifier nextId,
                int stepCount,
                int timeoutTicks,
                Set<Identifier> entityIds,
                Set<SpawnGroup> spawnGroups,
                List<Set<Identifier>> skipIfBootIds,
                List<Set<Identifier>> onlyIfBootIds) {
            this.name = name != null ? name : "unnamed";
            this.blockId = requireNonNull(blockId);
            this.nextId = requireNonNull(nextId);
            this.stepCount = stepCount;
            this.timeoutTicks = timeoutTicks;
            this.entityIds = emptySetIfNull(entityIds);
            this.spawnGroups = emptySetIfNull(spawnGroups);
            this.skipIfBootIds = emptyLiotIfNull(skipIfBootIds);
            this.onlyIfBootIds = emptyLiotIfNull(onlyIfBootIds);
            if (this.skipIfBootIds.isEmpty() && !this.onlyIfBootIds.isEmpty()) {
                throw new RuntimeException("Rules can't set both skipIfBootIds and onlyIfBootIds");
            }
        }

        private static <T> Set<T> emptySetIfNull(Set<T> set) {
            return set == null ? Collections.emptySet() : set;
        }

        private static <T> List<T> emptyLiotIfNull(List<T> list) {
            return list == null ? Collections.emptyList() : list;
        }
    }
}
