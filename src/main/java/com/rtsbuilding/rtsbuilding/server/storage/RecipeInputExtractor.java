package com.rtsbuilding.rtsbuilding.server.storage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

/**
 * RecipeInputExtractor — 从1.7.10的IRecipe中提取输入物品列表。
 *
 * 处理三种配方类型：
 * 1. ShapedRecipes (vanilla 有序合成) — 直接读取 recipeItems[] 字段
 * 2. ShapelessRecipes (vanilla 无序合成) — 直接读取 recipeItems 列表
 * 3. Forge OreDict 配方 — 通过反射调用 getInput() 方法
 */
public final class RecipeInputExtractor {

    private RecipeInputExtractor() {}

    /**
     * 从配方中提取输入物品的ID列表（去重，仅统计类型不计数量）。
     *
     * @return 输入物品ID列表（如 "minecraft:oak_planks"），可能为空
     */
    public static List<String> extractInputItemIds(IRecipe recipe) {
        if (recipe == null) return Collections.emptyList();

        List<String> result = new ArrayList<>();

        // 1. 尝试 ShapedRecipes
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            for (ItemStack stack : shaped.recipeItems) {
                String id = itemStackToId(stack);
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
            return result;
        }

        // 2. 尝试 ShapelessRecipes
        if (recipe instanceof ShapelessRecipes) {
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            for (ItemStack stack : shapeless.recipeItems) {
                String id = itemStackToId(stack);
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
            return result;
        }

        // 3. 尝试 OreDict 配方（Forge ShapedOreRecipe / ShapelessOreRecipe）
        List<ItemStack> oreInputs = extractOreDictInputs(recipe);
        for (ItemStack stack : oreInputs) {
            String id = itemStackToId(stack);
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }

        return result;
    }

    /**
     * 从配方中提取输入物品的具体ItemStack列表。
     * 用于计算所需数量。
     */
    public static List<ItemStack> extractInputStacks(IRecipe recipe) {
        if (recipe == null) return Collections.emptyList();

        // 1. ShapedRecipes
        if (recipe instanceof ShapedRecipes) {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : ((ShapedRecipes) recipe).recipeItems) {
                if (stack != null) {
                    result.add(stack.copy());
                }
            }
            return result;
        }

        // 2. ShapelessRecipes
        if (recipe instanceof ShapelessRecipes) {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : ((ShapelessRecipes) recipe).recipeItems) {
                if (stack != null) {
                    result.add(stack.copy());
                }
            }
            return result;
        }

        // 3. OreDict
        return extractOreDictInputs(recipe);
    }

    /**
     * 通过反射获取OreDict配方的输入物品。
     * Forge的ShapedOreRecipe和ShapelessOreRecipe有getInput()方法返回Object[]。
     * Object[]中可以包含 ItemStack, List&lt;ItemStack&gt;, 或 null。
     */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> extractOreDictInputs(IRecipe recipe) {
        List<ItemStack> result = new ArrayList<>();
        try {
            Method getInput = recipe.getClass()
                .getMethod("getInput");
            Object[] inputs = (Object[]) getInput.invoke(recipe);
            if (inputs == null) return result;

            for (Object input : inputs) {
                if (input == null) continue;
                if (input instanceof ItemStack) {
                    result.add(((ItemStack) input).copy());
                } else if (input instanceof List) {
                    List<ItemStack> list = (List<ItemStack>) input;
                    if (!list.isEmpty()) {
                        result.add(
                            list.get(0)
                                .copy()); // 取第一个作为代表
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            // 不是OreDict配方，尝试其他方式
            tryAlternativeExtraction(recipe, result);
        } catch (Exception e) {
            // 反射失败，静默降级
        }
        return result;
    }

    /**
     * 尝试通过各种字段名提取配方输入。
     */
    @SuppressWarnings("unchecked")
    private static void tryAlternativeExtraction(IRecipe recipe, List<ItemStack> result) {
        // 尝试常见字段名
        String[] candidateFields = { "input", "inputs", "recipeItems", "ingredients" };
        for (String fieldName : candidateFields) {
            try {
                Field field = findField(recipe.getClass(), fieldName);
                if (field == null) continue;
                field.setAccessible(true);
                Object value = field.get(recipe);
                if (value instanceof ItemStack[]) {
                    for (ItemStack s : (ItemStack[]) value) {
                        if (s != null) result.add(s.copy());
                    }
                    return;
                } else if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (item instanceof ItemStack) {
                            result.add(((ItemStack) item).copy());
                        }
                    }
                    return;
                } else if (value instanceof Object[]) {
                    for (Object item : (Object[]) value) {
                        if (item instanceof ItemStack) {
                            result.add(((ItemStack) item).copy());
                        } else if (item instanceof List && !((List<?>) item).isEmpty()) {
                            Object first = ((List<?>) item).get(0);
                            if (first instanceof ItemStack) {
                                result.add(((ItemStack) first).copy());
                            }
                        }
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String itemStackToId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        return net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
    }

    // ======== 批量获取所有配方 ========

    /**
     * 获取所有注册的合成配方列表。
     */
    @SuppressWarnings("unchecked")
    public static List<IRecipe> getAllRecipes() {
        try {
            return new ArrayList<>(
                CraftingManager.getInstance()
                    .getRecipeList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
