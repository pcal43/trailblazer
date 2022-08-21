package net.pcal.footpaths;

import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Runtime representation of configuration. FIXME should allow more than one RBC per block bootId
 */
@SuppressWarnings("ClassCanBeRecord")
class FootpathsRuntimeConfig {

    private final List<Rule> rules;
    private final Predicate<Identifier> entityMatcher;
    private final Predicate<Identifier> blockMatcher;

    private FootpathsRuntimeConfig(List<Rule> rules) {
        this.rules = requireNonNull(rules);
        this.entityMatcher = s -> true;
        this.blockMatcher = s -> true;
    }

    List<Rule> getRuleListForEntity(Entity entity) {
//        if (this.watchedEntityIds != null) {
//            // abort if it's not an entity we care about
//            final Identifier entityId = Registry.ENTITY_TYPE.getId(entity.getType());
//            if (this.watchedEntityIds.contains(entityId)) return;
//        }
//        if (this.watchedSpawnGroups != null) {
//            //FIXME logic is broken here
//
//            // bail if it's not an entity we care about
//            final SpawnGroup group = entity.getType().getSpawnGroup();
//            if (this.watchedSpawnGroups.contains(group.getName())) return;
//        }

    }

    Set<Rule> getRulesForBlock(Identifier block) {

    }

    public List<Rule> getRules() {
        return this.rules;
    }

    record Rule(
            Identifier blockId,
            Identifier nextId,
            int stepCount,
            int timeoutTicks,
            Set<Identifier> entityIds,
            Set<String> spawnGroups,
            Set<Identifier> skipIfBootIds,
            Set<Identifier> skipIfBootNbts,
            Set<Identifier> onlyIfBootIds,
            Set<Identifier> onlyIfBootNbts
    ) {

        Rule(
                Identifier blockId,
                Identifier nextId,
                int stepCount,
                int timeoutTicks,
                Set<Identifier> entityIds,
                Set<String> spawnGroups,
                Set<Identifier> skipIfBootIds,
                Set<Identifier> skipIfBootNbts,
                Set<Identifier> onlyIfBootIds,
                Set<Identifier> onlyIfBootNbts) {
            this.blockId = requireNonNull(blockId);
            this.nextId = requireNonNull(nextId);
            this.stepCount = stepCount;
            this.timeoutTicks = timeoutTicks;
            this.entityIds = emptySetIfNull(entityIds);
            this.spawnGroups = emptySetIfNull(spawnGroups);
            this.skipIfBootIds = emptySetIfNull(skipIfBootIds);
            this.skipIfBootNbts = emptySetIfNull(skipIfBootNbts);
            this.onlyIfBootIds = emptySetIfNull(onlyIfBootIds);
            this.onlyIfBootNbts = emptySetIfNull(onlyIfBootNbts);
        }

        private boolean hasBootRules() {
            return !(skipIfBootIds.isEmpty() && skipIfBootNbts.isEmpty()
                    && onlyIfBootIds.isEmpty() && onlyIfBootNbts.isEmpty());
        }

        private static <T> Set<T> emptySetIfNull(Set<T> set) {
            return set == null ? Collections.emptySet() : set;
        }
    }


}
