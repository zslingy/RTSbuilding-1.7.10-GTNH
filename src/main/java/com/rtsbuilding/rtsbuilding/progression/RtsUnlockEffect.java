package com.rtsbuilding.rtsbuilding.progression;

public class RtsUnlockEffect {

    private final Type type;
    private final RtsFeature feature;
    private final int value;

    public enum Type {
        UNLOCK_FEATURE,
        SET_RADIUS_BLOCKS,
        SET_FLUID_CAPACITY_BUCKETS,
        SET_ULTIMINE_LIMIT,
        BYPASS_HOME_RADIUS
    }

    public RtsUnlockEffect(Type type, RtsFeature feature, int value) {
        this.type = type;
        this.feature = feature;
        this.value = value;
    }

    public static RtsUnlockEffect unlock(RtsFeature feature) {
        return new RtsUnlockEffect(Type.UNLOCK_FEATURE, feature, 0);
    }

    public static RtsUnlockEffect radius(int blocks) {
        return new RtsUnlockEffect(Type.SET_RADIUS_BLOCKS, null, blocks);
    }

    public static RtsUnlockEffect fluidCapacityBuckets(int buckets) {
        return new RtsUnlockEffect(Type.SET_FLUID_CAPACITY_BUCKETS, null, buckets);
    }

    public static RtsUnlockEffect ultimineLimit(int blocks) {
        return new RtsUnlockEffect(Type.SET_ULTIMINE_LIMIT, null, blocks);
    }

    public static RtsUnlockEffect bypassHomeRadius() {
        return new RtsUnlockEffect(Type.BYPASS_HOME_RADIUS, null, 0);
    }

    public Type getType() {
        return type;
    }

    public RtsFeature getFeature() {
        return feature;
    }

    public int getValue() {
        return value;
    }
}
