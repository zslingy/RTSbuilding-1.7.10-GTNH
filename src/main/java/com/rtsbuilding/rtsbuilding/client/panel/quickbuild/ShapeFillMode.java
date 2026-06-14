package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

/**
 * 快速建造形状填充模式。
 */
public enum ShapeFillMode {

    FILL,
    HOLLOW,
    SKELETON;

    public static ShapeFillMode parse(String value) {
        if (value == null) return FILL;
        String normalized = value.trim()
            .toUpperCase();
        if ("WIREFRAME".equals(normalized)) return SKELETON;
        try {
            return ShapeFillMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return FILL;
        }
    }

    public String key() {
        return name().toLowerCase();
    }
}
