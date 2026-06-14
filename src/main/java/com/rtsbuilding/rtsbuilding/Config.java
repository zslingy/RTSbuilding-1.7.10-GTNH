package com.rtsbuilding.rtsbuilding;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

public class Config {

    // ---- Categories ----
    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_BLUEPRINTS = "blueprints";
    private static final String CATEGORY_PROGRESSION = "progression";
    private static final String CATEGORY_RENDERING = "rendering";

    // ---- Config values ----
    public static boolean enableSurvivalProgression = false;
    public static boolean shareSurvivalProgressionWithTeams = false;
    public static int maxActionRadiusBlocks = 128;
    public static boolean enableBlueprints = true;
    public static int maxBlueprintBlocks = 20000;
    public static String[] progressionCostOverrides = new String[0];
    public static boolean useWireframePreview = false;

    public static boolean rtsEnabled = true;
    public static boolean debugMode = false;

    private static Configuration config;

    public static void synchronizeConfiguration(File configFile) {
        config = new Configuration(configFile);

        config.load();

        enableSurvivalProgression = config.getBoolean(
            "enableSurvivalProgression",
            CATEGORY_PROGRESSION,
            false,
            "Enable RTS Building survival progression, feature unlocks, home anchors, and progression radius limits.");

        shareSurvivalProgressionWithTeams = config.getBoolean(
            "shareSurvivalProgressionWithTeams",
            CATEGORY_PROGRESSION,
            false,
            "When survival progression is enabled, share unlocked progression nodes and RTS home anchors with the player's team.");

        maxActionRadiusBlocks = config.getInt(
            "maxActionRadiusBlocks",
            CATEGORY_GENERAL,
            128,
            48,
            512,
            "Maximum RTS action radius in blocks. Used directly when survival progression is disabled, and by the Radius Max skill when survival progression is enabled.");

        enableBlueprints = config.getBoolean(
            "enableBlueprints",
            CATEGORY_BLUEPRINTS,
            true,
            "Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.");

        maxBlueprintBlocks = config.getInt(
            "maxBlueprintBlocks",
            CATEGORY_BLUEPRINTS,
            20000,
            1,
            200000,
            "Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.");

        progressionCostOverrides = config.getStringList(
            "progressionCostOverrides",
            CATEGORY_PROGRESSION,
            new String[0],
            "Skill material overrides. Format: node_path=minecraft:item:count,minecraft:item2:count. Example: ultimine=minecraft:diamond_pickaxe:1,minecraft:redstone_block:1");

        useWireframePreview = config.getBoolean(
            "useWireframePreview",
            CATEGORY_RENDERING,
            false,
            "Use wireframe outlines instead of translucent block models for placement previews and ghost animations.");

        if (config.hasChanged()) {
            config.save();
        }
    }

    // ---- Hot-reload setters (match original Config.java API) ----

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        enableSurvivalProgression = enabled;
        config.get(CATEGORY_PROGRESSION, "enableSurvivalProgression", false)
            .set(enabled);
        config.save();
    }

    public static int maxActionRadiusBlocks() {
        return maxActionRadiusBlocks;
    }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        maxActionRadiusBlocks = Math.max(48, Math.min(512, radiusBlocks));
        config.get(CATEGORY_GENERAL, "maxActionRadiusBlocks", 128)
            .set(maxActionRadiusBlocks);
        config.save();
    }

    public static boolean areBlueprintsEnabled() {
        return enableBlueprints;
    }

    public static int maxBlueprintBlocks() {
        return maxBlueprintBlocks;
    }

    public static void saveProgressionSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
        boolean blueprintsEnabled, int maxBlueprintBlocks, Map<String, String> costOverrides) {
        enableSurvivalProgression = survivalEnabled;
        shareSurvivalProgressionWithTeams = shareWithTeams;
        maxActionRadiusBlocks = Math.max(48, Math.min(512, radiusBlocks));
        enableBlueprints = blueprintsEnabled;
        Config.maxBlueprintBlocks = Math.max(1, Math.min(200000, maxBlueprintBlocks));
        setProgressionCostOverrides(costOverrides);

        config.get(CATEGORY_PROGRESSION, "enableSurvivalProgression", false)
            .set(survivalEnabled);
        config.get(CATEGORY_PROGRESSION, "shareSurvivalProgressionWithTeams", false)
            .set(shareWithTeams);
        config.get(CATEGORY_GENERAL, "maxActionRadiusBlocks", 128)
            .set(maxActionRadiusBlocks);
        config.get(CATEGORY_BLUEPRINTS, "enableBlueprints", true)
            .set(blueprintsEnabled);
        config.get(CATEGORY_BLUEPRINTS, "maxBlueprintBlocks", 20000)
            .set(Config.maxBlueprintBlocks);
        config.get(CATEGORY_PROGRESSION, "progressionCostOverrides", new String[0])
            .set(encodeCostOverrides(costOverrides));
        config.save();
    }

    public static Map<String, String> progressionCostOverrides() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : progressionCostOverrides) {
            if (raw == null) {
                continue;
            }
            int split = raw.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String node = raw.substring(0, split)
                .trim();
            String costs = raw.substring(split + 1)
                .trim();
            if (!node.isEmpty()) {
                out.put(node, costs);
            }
        }
        return out;
    }

    public static boolean isWireframePreviewEnabled() {
        return useWireframePreview;
    }

    public static void setWireframePreviewEnabled(boolean enabled) {
        useWireframePreview = enabled;
        config.get(CATEGORY_RENDERING, "useWireframePreview", false)
            .set(enabled);
        config.save();
    }

    public static void setProgressionCostOverride(String nodePath, String costsText) {
        if (nodePath == null || nodePath.isEmpty()) {
            return;
        }
        Map<String, String> current = progressionCostOverrides();
        String clean = costsText == null ? "" : costsText.trim();
        if (clean.isEmpty()) {
            current.remove(nodePath);
        } else {
            current.put(nodePath, clean);
        }
        setProgressionCostOverrides(current);
        config.save();
    }

    private static void setProgressionCostOverrides(Map<String, String> overrides) {
        progressionCostOverrides = encodeCostOverrides(overrides);
        config.get(CATEGORY_PROGRESSION, "progressionCostOverrides", new String[0])
            .set(progressionCostOverrides);
    }

    private static String[] encodeCostOverrides(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return new String[0];
        }
        List<String> encoded = new ArrayList<>(overrides.size());
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String node = entry.getKey() == null ? ""
                : entry.getKey()
                    .trim();
            String costs = entry.getValue() == null ? ""
                : entry.getValue()
                    .trim();
            if (!node.isEmpty() && !costs.isEmpty()) {
                encoded.add(node + "=" + costs);
            }
        }
        return encoded.toArray(new String[0]);
    }
}
