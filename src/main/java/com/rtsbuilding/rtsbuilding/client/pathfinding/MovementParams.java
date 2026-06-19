package com.rtsbuilding.rtsbuilding.client.pathfinding;

public class MovementParams {

    public final double targetX;
    public final double targetY;
    public final double targetZ;
    public final float speed;
    public final boolean canOpenDoors;
    public final int timeoutTicks;

    public MovementParams(double targetX, double targetY, double targetZ, float speed, boolean canOpenDoors,
        int timeoutTicks) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.speed = speed;
        this.canOpenDoors = canOpenDoors;
        this.timeoutTicks = timeoutTicks;
    }

    public static MovementParams walk(double x, double y, double z) {
        return new MovementParams(x, y, z, 1.0F, true, 200);
    }

    public static MovementParams sprint(double x, double y, double z) {
        return new MovementParams(x, y, z, 1.3F, true, 160);
    }

    public static MovementParams fly(double x, double y, double z) {
        return new MovementParams(x, y, z, 1.5F, false, 300);
    }
}
