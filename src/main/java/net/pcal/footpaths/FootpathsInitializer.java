package net.pcal.footpaths;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final String CONFIG_RESOURCE_NAME = "footpaths-default.json5";

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
        final GsonConfig gsonConfig = gson.fromJson(stripComments(effectiveConfigRaw), GsonConfig.class);
        FootpathsService.getInstance().configure(loadConfig(gsonConfig));
        //
        // All done
        //
        logger.info(LOG_PREFIX + "Initialized" + (isCustomConfig ? " with custom configuration." : "."));
    }

    // ===================================================================================
    // Private methods

    private static FootpathsRuntimeConfig loadConfig(GsonConfig config) {
        requireNonNull(config);
        final FootpathsRuntimeConfig.Builder builder = FootpathsRuntimeConfig.builder();
        for(GsonBlockConfig gsonBlock : config.blocks) {
            final FootpathsRuntimeConfig.Rule rule = new FootpathsRuntimeConfig.Rule(
                    new Identifier(requireNonNull(gsonBlock.id)),
                    new Identifier(requireNonNull(gsonBlock.nextId)),
                    requireNonNull(gsonBlock.stepCount),
                    requireNonNull(gsonBlock.timeoutTicks),
                    toIdentifierSet(gsonBlock.entityIds),
                    ImmutableSet.copyOf(gsonBlock.spawnGroups),
                    toIdentifierSet(gsonBlock.skipIfBootIds),
                    toIdentifierSet(gsonBlock.skipIfBootNbts),
                    toIdentifierSet(gsonBlock.onlyIfBootIds),
                    toIdentifierSet(gsonBlock.onlyIfBootNbts)
            );
            builder.rule(rule);
        }
        return builder.build();
    }

    private static Set<Identifier> toIdentifierSet(List<String> rawIds) {
        final ImmutableSet.Builder<Identifier> builder = ImmutableSet.builder();
        for(String rawId : rawIds) {
            builder.add(new Identifier(rawId));
        }
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

    public static class GsonConfig {
        public List<GsonBlockConfig> blocks;
    }

    public static class GsonBlockConfig {
        public String id;
        public String nextId;
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