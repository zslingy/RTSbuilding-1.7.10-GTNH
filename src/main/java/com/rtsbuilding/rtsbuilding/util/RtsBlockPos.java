package com.rtsbuilding.rtsbuilding.util;

/** Minimal BlockPos replacement for Forge 1.7.10 (no native BlockPos in vanilla). */
public final class RtsBlockPos {

    private final int x;
    private final int y;
    private final int z;

    public RtsBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RtsBlockPos)) return false;
        RtsBlockPos that = (RtsBlockPos) o;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return ((x * 31) + y) * 31 + z;
    }

    @Override
    public String toString() {
        return "BlockPos{" + x + ", " + y + ", " + z + '}';
    }
}
