package com.rtsbuilding.rtsbuilding.server.storage;

public class GuiBinding {

    public final int x;
    public final int y;
    public final int z;
    public final int dimensionId;
    public final byte face;

    public GuiBinding() {
        this(0, 0, 0, 0, (byte) 0);
    }

    public GuiBinding(int x, int y, int z, int dimensionId, byte face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionId = dimensionId;
        this.face = face;
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

    public int getDimensionId() {
        return dimensionId;
    }

    public byte getFace() {
        return face;
    }
}
