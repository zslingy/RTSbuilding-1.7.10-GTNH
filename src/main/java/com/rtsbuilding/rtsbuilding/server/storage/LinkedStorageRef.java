package com.rtsbuilding.rtsbuilding.server.storage;

/**
 * 已链接存储方块的稳定身份标识。
 * 对齐原版 LinkedStorageRef(dimension, BlockPos)，提供稳定的身份标识，不依赖方块实体是否存活。
 * 不同维度中相同坐标的方块具有独立身份。
 */
public final class LinkedStorageRef {

    public final int dimension;
    public final int x;
    public final int y;
    public final int z;

    public LinkedStorageRef(int dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkedStorageRef)) return false;
        LinkedStorageRef other = (LinkedStorageRef) o;
        return dimension == other.dimension && x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        int result = dimension;
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "LinkedStorageRef{dim=" + dimension + ", x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}
