package com.rtsbuilding.rtsbuilding.blueprint;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

public final class BlueprintReplaceRules {

    /** Tag key identifier — resolved at runtime in stage 3 handler. */
    public static final ResourceLocation SOFT_REPLACEABLE_TAG = new ResourceLocation(
        RtsbuildingMod.MODID,
        "blueprint_soft_replaceable");

    private static final Set<ResourceLocation> VANILLA_SOFT_REPLACEABLE = new HashSet<>();

    static {
        String[] ids = { "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy",
            "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush",
            "peony", "brown_mushroom", "red_mushroom", "vine", "lily_pad", "seagrass", "tall_seagrass", "sugar_cane",
            "cactus", "grass", "tall_grass", "snow", "snow_layer", "cobweb" };
        for (String id : ids) {
            VANILLA_SOFT_REPLACEABLE.add(vanilla(id));
        }
    }

    private BlueprintReplaceRules() {}

    public static boolean isSoftReplaceable(ResourceLocation blockId) {
        if (blockId == null) {
            return false;
        }
        return VANILLA_SOFT_REPLACEABLE.contains(blockId);
    }

    public static boolean isTaggedSoftReplaceable(ResourceLocation blockId) {
        if (blockId == null) {
            return false;
        }
        return VANILLA_SOFT_REPLACEABLE.contains(blockId);
    }

    public static boolean canBlueprintReplace(net.minecraft.world.World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        net.minecraft.block.Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) {
            return true;
        }
        String regName = net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        if (regName == null) {
            return false;
        }
        ResourceLocation id = new ResourceLocation(regName);
        return VANILLA_SOFT_REPLACEABLE.contains(id);
    }

    private static ResourceLocation vanilla(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
