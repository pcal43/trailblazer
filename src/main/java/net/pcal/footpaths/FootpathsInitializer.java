package net.pcal.footpaths;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.pcal.footpaths.FootpathsRuntimeConfig.Rule;
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
import static net.pcal.footpaths.FootpathsService.LOGGER_NAME;
import static net.pcal.footpaths.FootpathsService.LOG_PREFIX;

public class FootpathsInitializer implements ModInitializer {

    // ===================================================================================
    // Constants

    private static final Path CUSTOM_CONFIG_PATH = Paths.get("config", "footpaths.json5");
    private static final Path DEFAULT_CONFIG_PATH = Paths.get("config", "footpaths-default.json5");
    private static final Set<Identifier> DEFAULT_ENTITY_IDS = ImmutableSet.of(new Identifier("minecraft:player"));
    private static final String CONFIG_RESOURCE_NAME = "footpaths-default.json5";
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
        try (InputStream in = FootpathsInitializer.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE_NAME)) {
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
        FootpathsService.getInstance().configure(loadConfig(gsonConfig));
        //
        // All done
        //
        logger.info(LOG_PREFIX + "Initialized" + (isCustomConfig ? " with custom configuration." : "."));
    }

    private static FootpathsRuntimeConfig loadConfig(GsonModConfig config) {
        requireNonNull(config);
        final ImmutableList.Builder<Rule> builder = ImmutableList.builder();
        for (final GsonRuleConfig gsonBlock : config.rules) {
            final Rule rule = new Rule(
                    new Identifier(requireNonNull(gsonBlock.blockId)),
                    new Identifier(requireNonNull(gsonBlock.nextBlockId)),
                    gsonBlock.stepCount != null ? gsonBlock.stepCount : DEFAULT_STEP_COUNT,
                    gsonBlock.timeoutTicks != null ? gsonBlock.timeoutTicks : DEFAULT_TIMEOUT_TICKS,
                    gsonBlock.entityIds != null ? toIdentifierSet(gsonBlock.entityIds) : DEFAULT_ENTITY_IDS,
                    toSpawnGroupList(gsonBlock.spawnGroups),
                    toIdentifierSet(gsonBlock.skipIfBootIds),
                    toIdentifierSet(gsonBlock.skipIfBootNbts),
                    toIdentifierSet(gsonBlock.onlyIfBootIds),
                    toIdentifierSet(gsonBlock.onlyIfBootNbts)
            );
            builder.add(rule);
        }
        return new FootpathsRuntimeConfig(builder.build());
    }

    private static Set<Identifier> toIdentifierSet(List<String> rawIds) {
        if (rawIds == null) return Collections.emptySet();
        final ImmutableSet.Builder<Identifier> builder = ImmutableSet.builder();
        for (String rawId : rawIds) {
            builder.add(new Identifier(rawId));
        }
        return builder.build();
    }

    private static Set<SpawnGroup> toSpawnGroupList(Iterable<String> rawIds) {
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
    // Gson model

    public static class GsonModConfig {
        public List<GsonRuleConfig> rules;
    }

    public static class GsonRuleConfig {
        public String blockId;
        public String nextBlockId;
        Integer timeoutTicks;
        Integer stepCount;
        List<String> entityIds;
        List<String> spawnGroups;
        List<String> onlyIfBootIds;
        List<String> onlyIfBootNbts;
        List<String> skipIfBootIds;
        List<String> skipIfBootNbts;
    }
}