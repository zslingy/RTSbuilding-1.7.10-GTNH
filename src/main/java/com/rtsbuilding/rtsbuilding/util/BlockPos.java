package com.rtsbuilding.rtsbuilding.util;

/**
 * BlockPos for 1.7.10 port, compatible with 1.21.1 net.minecraft.core.BlockPos API.
 * Forge 1.7.10 has no native BlockPos class, so we provide one.
 */
public final class BlockPos {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
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
        if (!(o instanceof BlockPos)) return false;
        BlockPos that = (BlockPos) o;
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
