package net.pcal.trailblazer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.pcal.trailblazer.TrailblazerRuntimeConfig.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static net.pcal.trailblazer.TrailblazerService.LOGGER_NAME;
import static net.pcal.trailblazer.TrailblazerService.LOG_PREFIX;

public class TrailblazerInitializer implements ModInitializer {

    // ===================================================================================
    // Constants

    private static final Path CUSTOM_CONFIG_PATH = Paths.get("config", "footpaths.json5");
    private static final Path DEFAULT_CONFIG_PATH = Paths.get("config", "trailblazer-default.json5");
    private static final Set<Identifier> DEFAULT_ENTITY_IDS = ImmutableSet.of(new Identifier("minecraft:player"));
    private static final String CONFIG_RESOURCE_NAME = "trailblazer-default.json5";
    public static final int DEFAULT_STEP_COUNT = 0;
    public static final int DEFAULT_TIMEOUT_TICKS = 72000;

    // ===================================================================================
    // ModInitializer implementation

    @Override
    public void onInitialize() {
        try {
            initialize();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    // ===================================================================================
    // Private methods

    private void initialize() throws IOException {
        final Logger logger = LogManager.getLogger(LOGGER_NAME);
        //
        // Load the default configuration from resources and write it as the -default in the installation
        //
        final String defaultConfigResourceRaw;
        try (InputStream in = TrailblazerInitializer.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE_NAME)) {
            if (in == null) {
                throw new FileNotFoundException("Unable to load resource " + CONFIG_RESOURCE_NAME); // wat
            }
            defaultConfigResourceRaw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        DEFAULT_CONFIG_PATH.getParent().toFile().mkdirs();
        Files.writeString(DEFAULT_CONFIG_PATH, defaultConfigResourceRaw);
        //
        // Figure out whether to use custom or default config
        //
        final boolean isCustomConfig;
        final String effectiveConfigRaw;
        if (CUSTOM_CONFIG_PATH.toFile().exists()) {
            logger.info(LOG_PREFIX + "Using custom configuration.");
            effectiveConfigRaw = Files.readString(CUSTOM_CONFIG_PATH);
            isCustomConfig = true;
        } else {
            effectiveConfigRaw = defaultConfigResourceRaw;
            isCustomConfig = false;
        }
        //
        // Apply the config
        //
        final Gson gson = new Gson();
        final GsonModConfig gsonConfig = gson.fromJson(stripComments(effectiveConfigRaw), GsonModConfig.class);
        TrailblazerService.getInstance().configure(loadConfig(gsonConfig));
        //
        // All done
        //
        logger.info(LOG_PREFIX + "Initialized" + (isCustomConfig ? " with custom configuration." : "."));
    }

    private static TrailblazerRuntimeConfig loadConfig(GsonModConfig config) {
        requireNonNull(config);
        final ImmutableList.Builder<Rule> builder = ImmutableList.builder();
        for (int i=0; i < config.rules.size(); i++) {
            final GsonRuleConfig gsonRule = config.rules.get(i);
            final Rule rule = new Rule(
                    gsonRule.name != null ? gsonRule.name : "rule-"+i,
                    new Identifier(requireNonNull(gsonRule.blockId)),
                    new Identifier(requireNonNull(gsonRule.nextBlockId)),
                    gsonRule.stepCount != null ? gsonRule.stepCount : DEFAULT_STEP_COUNT,
                    gsonRule.timeoutTicks != null ? gsonRule.timeoutTicks : DEFAULT_TIMEOUT_TICKS,
                    gsonRule.entityIds != null ? toIdentifierSet(gsonRule.entityIds) : DEFAULT_ENTITY_IDS,
                    toSpawnGroupList(gsonRule.spawnGroups),
                    toIdentifierSetList(gsonRule.skipIfBoots),
                    toIdentifierSetList(gsonRule.onlyIfBoots)
            );
            builder.add(rule);
        }
        return new TrailblazerRuntimeConfig(builder.build());
    }

    private static Set<Identifier> toIdentifierSet(List<String> rawIds) {
        if (rawIds == null) return Collections.emptySet();
        final ImmutableSet.Builder<Identifier> builder = ImmutableSet.builder();
        for (String rawId : rawIds) {
            builder.add(new Identifier(rawId));
        }
        return builder.build();
    }

    private static List<Set<Identifier>> toIdentifierSetList(List<List<String>> rawIdLists) {
        if (rawIdLists == null) return Collections.emptyList();
        final ImmutableList.Builder<Set<Identifier>> builder = ImmutableList.builder();
        for (List<String> rawIdList : rawIdLists) {
            builder.add(toIdentifierSet(rawIdList));
        }
        return builder.build();
    }

    private static Set<SpawnGroup> toSpawnGroupList(Iterable<String> rawIds) {
        if (rawIds == null) return Collections.emptySet();
        final ImmutableSet.Builder<SpawnGroup> builder = ImmutableSet.builder();
        rawIds.forEach(sg -> builder.add(SpawnGroup.valueOf(sg)));
        return builder.build();
    }

    private static String stripComments(String json) throws IOException {
        final StringBuilder out = new StringBuilder();
        final BufferedReader br = new BufferedReader(new StringReader(json));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.strip().startsWith(("//"))) out.append(line).append('\n');
        }
        return out.toString();
    }

    // ===================================================================================
    // Gson bindings

    public static class GsonModConfig {
        List<GsonRuleConfig> rules;
    }

    public static class GsonRuleConfig {
        String name;
        String blockId;
        String nextBlockId;
        Integer timeoutTicks;
        Integer stepCount;
        List<String> entityIds;
        List<String> spawnGroups;
        List<List<String>> onlyIfBoots;
        List<List<String>> skipIfBoots;
    }
}