package com.rtsbuilding.rtsbuilding.server.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/**
 * 配方扫描缓存 — 阶段7性能优化。
 * 将 CraftingManager.getRecipeList() 的遍历+提取结果缓存，
 * 仅在服务端启动/配方重载时重建。
 *
 * 线程安全：双重检查锁 + volatile。
 */
public final class RecipeScanCache {

    private static volatile List<CachedRecipe> cache;
    private static volatile boolean dirty = true;

    private RecipeScanCache() {}

    /** 标记缓存失效 — 服务端启动或配方重载时调用 */
    public static void markDirty() {
        dirty = true;
    }

    /** 获取或重建缓存（惰性 + 线程安全） */
    public static List<CachedRecipe> getOrRebuild() {
        if (!dirty && cache != null) return cache;
        synchronized (RecipeScanCache.class) {
            if (!dirty && cache != null) return cache;
            cache = build();
            dirty = false;
        }
        return cache;
    }

    /** 强制立即重建（用于明确知道配方已变更的场景） */
    public static void rebuildNow() {
        synchronized (RecipeScanCache.class) {
            cache = build();
            dirty = false;
        }
    }

    /** 缓存是否有效 */
    public static boolean isValid() {
        return !dirty && cache != null;
    }

    @SuppressWarnings("unchecked")
    private static List<CachedRecipe> build() {
        List<CachedRecipe> result = new ArrayList<>();
        try {
            List<IRecipe> recipes = CraftingManager.getInstance()
                .getRecipeList();
            for (IRecipe recipe : recipes) {
                if (recipe == null) continue;
                ItemStack output = recipe.getRecipeOutput();
                if (output == null || output.getItem() == null) continue;
                String itemId = Item.itemRegistry.getNameForObject(output.getItem());
                if (itemId == null || itemId.isEmpty()) continue;

                String displayName = output.getDisplayName();
                if (displayName == null) displayName = itemId;

                List<String> inputIds = RecipeInputExtractor.extractInputItemIds(recipe);
                List<ItemStack> inputStacks = RecipeInputExtractor.extractInputStacks(recipe);

                result.add(new CachedRecipe(itemId, displayName, output.stackSize, inputIds, inputStacks));
            }
        } catch (Exception e) {
            // 构建失败时返回空列表，让调用方降级
        }
        return result;
    }

    /**
     * 缓存的配方数据 — 预提取 itemId / displayName / 输入列表。
     */
    public static class CachedRecipe {

        public final String itemId;
        public final String displayName;
        public final int count;
        public final List<String> inputIds;
        public final List<ItemStack> inputStacks;

        public CachedRecipe(String itemId, String displayName, int count, List<String> inputIds,
            List<ItemStack> inputStacks) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.count = count;
            this.inputIds = inputIds != null ? inputIds : Collections.<String>emptyList();
            this.inputStacks = inputStacks != null ? inputStacks : Collections.<ItemStack>emptyList();
        }
    }
}
