package com.rtsbuilding.rtsbuilding.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

public final class RtsProgressionNodes {

    public static final ResourceLocation CAMERA_CORE = id("camera_core");
    public static final ResourceLocation RADIUS_1 = id("radius_1");
    public static final ResourceLocation RADIUS_2 = id("radius_2");
    public static final ResourceLocation RADIUS_3 = id("radius_3");
    public static final ResourceLocation RADIUS_MAX = id("radius_max");
    public static final ResourceLocation STORAGE_LINK = id("storage_link");
    public static final ResourceLocation REMOTE_PLACE = id("remote_place");
    public static final ResourceLocation REMOTE_BREAK = id("remote_break");
    public static final ResourceLocation ROTATE_BLOCK = id("rotate_block");
    public static final ResourceLocation AUTO_STORE_MINED = id("auto_store_mined");
    public static final ResourceLocation FUNNEL = id("funnel");
    public static final ResourceLocation FLUID_BUFFER = id("fluid_buffer");
    public static final ResourceLocation REMOTE_GUI = id("remote_gui");
    public static final ResourceLocation CRAFT_TERMINAL = id("craft_terminal");
    public static final ResourceLocation JEI_TRANSFER = id("jei_transfer");
    public static final ResourceLocation ULTIMINE = id("ultimine");
    public static final ResourceLocation AREA_DESTROY = id("area_destroy");
    public static final ResourceLocation BLUEPRINTS = id("blueprints");
    public static final ResourceLocation FIELD_DEPLOYMENT = id("field_deployment");

    private static final Map<ResourceLocation, RtsProgressionNode> NODES = buildNodes();
    private static volatile Map<String, String> syncedCostOverrides = Collections.emptyMap();
    private static volatile boolean hasSyncedCostOverrides;

    private RtsProgressionNodes() {}

    public static RtsProgressionNode get(ResourceLocation id) {
        return NODES.get(id);
    }

    public static Collection<RtsProgressionNode> all() {
        return NODES.values();
    }

    public static List<RtsIngredientCost> costsFor(RtsProgressionNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        String override = syncedCostOverrides.get(
            node.getId()
                .getResourcePath());
        if (override == null) {
            override = Config.progressionCostOverrides()
                .get(
                    node.getId()
                        .getResourcePath());
        }
        if (override == null) {
            return node.getCosts();
        }
        return parseCostText(override, node.getCosts());
    }

    public static List<RtsIngredientCost> syncedCostsFor(RtsProgressionNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        if (!hasSyncedCostOverrides) {
            return costsFor(node);
        }
        String override = syncedCostOverrides.get(
            node.getId()
                .getResourcePath());
        return override == null ? node.getCosts() : parseCostText(override, node.getCosts());
    }

    public static void applySyncedCostOverrides(List<String> overrides) {
        hasSyncedCostOverrides = true;
        if (overrides == null || overrides.isEmpty()) {
            syncedCostOverrides = Collections.emptyMap();
            return;
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String raw : overrides) {
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
        syncedCostOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(out));
    }

    public static String costTextFor(RtsProgressionNode node) {
        return formatCostText(costsFor(node));
    }

    public static boolean contains(ResourceLocation id) {
        return NODES.containsKey(id);
    }

    private static Map<ResourceLocation, RtsProgressionNode> buildNodes() {
        LinkedHashMap<ResourceLocation, RtsProgressionNode> nodes = new LinkedHashMap<>();

        add(
            nodes,
            CAMERA_CORE,
            Collections.<ResourceLocation>emptyList(),
            Collections.<RtsIngredientCost>emptyList(),
            new ArrayList<RtsUnlockEffect>() {

                {
                    add(RtsUnlockEffect.unlock(RtsFeature.CAMERA));
                    add(RtsUnlockEffect.unlock(RtsFeature.INTERACT));
                    add(RtsUnlockEffect.radius(16));
                }
            },
            0,
            0);

        add(
            nodes,
            RADIUS_1,
            Collections.singletonList(CAMERA_CORE),
            cost("minecraft:glass", 8),
            Collections.singletonList(RtsUnlockEffect.radius(16)),
            1,
            0);
        add(
            nodes,
            RADIUS_2,
            Collections.singletonList(RADIUS_1),
            cost("minecraft:redstone", 12),
            Collections.singletonList(RtsUnlockEffect.radius(32)),
            2,
            0);
        add(
            nodes,
            RADIUS_3,
            Collections.singletonList(RADIUS_2),
            cost("minecraft:ender_pearl", 2),
            Collections.singletonList(RtsUnlockEffect.radius(48)),
            3,
            0);
        add(
            nodes,
            RADIUS_MAX,
            Collections.singletonList(RADIUS_3),
            cost("minecraft:netherite_ingot", 1),
            Collections.singletonList(RtsUnlockEffect.radius(Config.maxActionRadiusBlocks())),
            4,
            0);

        List<RtsUnlockEffect> storageEffects = new ArrayList<>();
        storageEffects.add(RtsUnlockEffect.unlock(RtsFeature.LINK_STORAGE));
        storageEffects.add(RtsUnlockEffect.unlock(RtsFeature.STORAGE_BROWSER));
        add(
            nodes,
            STORAGE_LINK,
            Collections.singletonList(CAMERA_CORE),
            cost("minecraft:chest", 2, "minecraft:redstone", 8),
            storageEffects,
            1,
            1);

        add(
            nodes,
            REMOTE_PLACE,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:copper_ingot", 16),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.REMOTE_PLACE)),
            2,
            1);
        add(
            nodes,
            REMOTE_BREAK,
            Collections.singletonList(REMOTE_PLACE),
            cost("minecraft:iron_pickaxe", 1, "minecraft:redstone", 8),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.REMOTE_BREAK)),
            3,
            1);
        add(
            nodes,
            ROTATE_BLOCK,
            Collections.singletonList(CAMERA_CORE),
            cost("minecraft:stick", 4, "minecraft:copper_ingot", 8),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.ROTATE_BLOCK)),
            1,
            -1);
        add(
            nodes,
            BLUEPRINTS,
            Collections.singletonList(CAMERA_CORE),
            cost("minecraft:paper", 1, "minecraft:lapis_lazuli", 1),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.BLUEPRINTS)),
            1,
            3);

        add(
            nodes,
            AUTO_STORE_MINED,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:hopper", 1),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.AUTO_STORE_MINED_DROPS)),
            2,
            2);
        add(
            nodes,
            FUNNEL,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:hopper", 4, "minecraft:redstone", 8),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.FUNNEL)),
            3,
            2);

        List<RtsUnlockEffect> fluidEffects = new ArrayList<>();
        fluidEffects.add(RtsUnlockEffect.unlock(RtsFeature.FLUID_HANDLING));
        fluidEffects.add(RtsUnlockEffect.fluidCapacityBuckets(100));
        add(
            nodes,
            FLUID_BUFFER,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:bucket", 4, "minecraft:iron_ingot", 16),
            fluidEffects,
            2,
            3);

        add(
            nodes,
            REMOTE_GUI,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:comparator", 1, "minecraft:redstone", 16),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.REMOTE_GUI_BINDING)),
            2,
            -1);
        add(
            nodes,
            CRAFT_TERMINAL,
            Collections.singletonList(STORAGE_LINK),
            cost("minecraft:crafting_table", 1, "minecraft:iron_ingot", 12),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.CRAFT_TERMINAL)),
            3,
            -1);
        add(
            nodes,
            JEI_TRANSFER,
            Collections.singletonList(CRAFT_TERMINAL),
            cost("minecraft:book", 1, "minecraft:lapis_lazuli", 8),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.JEI_TRANSFER)),
            4,
            -1);

        List<RtsUnlockEffect> ultimineEffects = new ArrayList<>();
        ultimineEffects.add(RtsUnlockEffect.unlock(RtsFeature.ULTIMINE));
        ultimineEffects.add(RtsUnlockEffect.ultimineLimit(64));
        add(
            nodes,
            ULTIMINE,
            Collections.singletonList(AUTO_STORE_MINED),
            cost("minecraft:diamond_pickaxe", 1, "minecraft:redstone_block", 1),
            ultimineEffects,
            3,
            3);
        add(
            nodes,
            AREA_DESTROY,
            Collections.singletonList(ULTIMINE),
            cost("minecraft:beacon", 1),
            Collections.singletonList(RtsUnlockEffect.unlock(RtsFeature.AREA_DESTROY)),
            4,
            3);
        add(
            nodes,
            FIELD_DEPLOYMENT,
            Collections.singletonList(RADIUS_MAX),
            cost("minecraft:dragon_head", 1),
            Collections.singletonList(RtsUnlockEffect.bypassHomeRadius()),
            5,
            0);

        return Collections.unmodifiableMap(nodes);
    }

    private static void add(Map<ResourceLocation, RtsProgressionNode> nodes, ResourceLocation id,
        List<ResourceLocation> dependencies, List<RtsIngredientCost> costs, List<RtsUnlockEffect> effects, int x,
        int y) {
        nodes.put(
            id,
            new RtsProgressionNode(
                id,
                "rtsbuilding.progression." + id.getResourcePath(),
                "rtsbuilding.progression." + id.getResourcePath() + ".desc",
                dependencies,
                costs,
                effects,
                x,
                y));
    }

    private static List<RtsIngredientCost> cost(Object... parts) {
        if (parts.length % 2 != 0) {
            throw new IllegalArgumentException("Cost arguments must be item/count pairs");
        }
        ArrayList<RtsIngredientCost> out = new ArrayList<>(parts.length / 2);
        for (int i = 0; i < parts.length; i += 2) {
            out.add(new RtsIngredientCost(new ResourceLocation((String) parts[i]), (Integer) parts[i + 1]));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<RtsIngredientCost> parseCostText(String text, List<RtsIngredientCost> fallback) {
        if (text == null || text.trim()
            .isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<RtsIngredientCost> out = new ArrayList<>();
        String[] parts = text.split(",");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int split = part.lastIndexOf(':');
            if (split <= 0 || split >= part.length() - 1) {
                return fallback;
            }
            try {
                ResourceLocation itemId = new ResourceLocation(part.substring(0, split));
                int count = Math.max(1, Integer.parseInt(part.substring(split + 1)));
                out.add(new RtsIngredientCost(itemId, count));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static String formatCostText(List<RtsIngredientCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return "";
        }
        ArrayList<String> parts = new ArrayList<>(costs.size());
        for (RtsIngredientCost cost : costs) {
            parts.add(cost.getItemId() + ":" + cost.getCount());
        }
        return join(",", parts);
    }

    private static String join(String delimiter, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(RtsbuildingMod.MODID, path);
    }
}
