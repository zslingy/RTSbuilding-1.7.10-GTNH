package com.rtsbuilding.rtsbuilding.common;

/**
 * 统一形状枚举 — 定义 RTS 模式支持的几何形状。
 * 从 client/panel/quickbuild 提升到 common 包，供客户端和服务端共用。
 */
public enum BuildShape {

    BLOCK,
    LINE,
    SQUARE,
    WALL,
    CIRCLE,
    BOX;

    public static BuildShape fromOrdinal(int ordinal) {
        BuildShape[] values = values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return BLOCK;
    }
}
