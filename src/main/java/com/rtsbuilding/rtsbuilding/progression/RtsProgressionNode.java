package com.rtsbuilding.rtsbuilding.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ResourceLocation;

public class RtsProgressionNode {

    private final ResourceLocation id;
    private final String titleKey;
    private final String descriptionKey;
    private final List<ResourceLocation> dependencies;
    private final List<RtsIngredientCost> costs;
    private final List<RtsUnlockEffect> effects;
    private final int x;
    private final int y;

    public RtsProgressionNode(ResourceLocation id, String titleKey, String descriptionKey,
        List<ResourceLocation> dependencies, List<RtsIngredientCost> costs, List<RtsUnlockEffect> effects, int x,
        int y) {
        this.id = id;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        this.costs = Collections.unmodifiableList(new ArrayList<>(costs));
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
        this.x = x;
        this.y = y;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public List<ResourceLocation> getDependencies() {
        return dependencies;
    }

    public List<RtsIngredientCost> getCosts() {
        return costs;
    }

    public List<RtsUnlockEffect> getEffects() {
        return effects;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
