package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.util.ResourceLocation;

public class GuiBinding {

    private ResourceLocation guiId;
    private int x, y, z;

    public GuiBinding() {}

    public GuiBinding(ResourceLocation guiId, int x, int y, int z) {
        this.guiId = guiId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ResourceLocation getGuiId() {
        return guiId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
