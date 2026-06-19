package com.rtsbuilding.rtsbuilding.server.pipeline.core;

public final class TypedKey<T> {

    private final String name;
    private final Class<T> type;

    public TypedKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }
}
