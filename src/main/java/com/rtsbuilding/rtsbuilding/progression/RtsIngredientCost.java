package com.rtsbuilding.rtsbuilding.progression;

import net.minecraft.util.ResourceLocation;

public class RtsIngredientCost {

    private final ResourceLocation itemId;
    private final int count;

    public RtsIngredientCost(ResourceLocation itemId, int count) {
        this.itemId = itemId;
        this.count = Math.max(1, count);
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RtsIngredientCost)) return false;
        RtsIngredientCost that = (RtsIngredientCost) o;
        return count == that.count && itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return 31 * itemId.hashCode() + count;
    }

    @Override
    public String toString() {
        return "RtsIngredientCost{itemId=" + itemId + ", count=" + count + '}';
    }
}
