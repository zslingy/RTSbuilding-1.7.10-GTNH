package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public final class BlueprintTransform {

    private BlueprintTransform() {}

    public static int normalizeSteps(int steps) {
        return Math.floorMod(steps, 4);
    }

    public static RtsBlockPos rotate(RtsBlockPos pos, int ySteps, int xSteps, int zSteps) {
        if (pos == null) {
            return new RtsBlockPos(0, 0, 0);
        }
        int[] xyz = rotateRaw(pos.getX(), pos.getY(), pos.getZ(), ySteps, xSteps, zSteps);
        return new RtsBlockPos(xyz[0], xyz[1], xyz[2]);
    }

    public static RtsBlockPos centerRotationOffset(int sizeX, int sizeY, int sizeZ, int ySteps, int xSteps,
        int zSteps) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return new RtsBlockPos(0, 0, 0);
        }
        RtsBlockPos pivot = new RtsBlockPos(sizeX / 2, sizeY / 2, sizeZ / 2);
        RtsBlockPos rotated = rotate(pivot, ySteps, xSteps, zSteps);
        return new RtsBlockPos(
            pivot.getX() - rotated.getX(),
            pivot.getY() - rotated.getY(),
            pivot.getZ() - rotated.getZ());
    }

    public static RtsBlockPos applyOffset(RtsBlockPos pos, RtsBlockPos offset) {
        if (pos == null) return new RtsBlockPos(0, 0, 0);
        if (offset == null) return pos;
        return new RtsBlockPos(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
    }

    public static int[] rotateRaw(int x, int y, int z, int ySteps, int xSteps, int zSteps) {
        ySteps = normalizeSteps(ySteps);
        xSteps = normalizeSteps(xSteps);
        zSteps = normalizeSteps(zSteps);
        for (int i = 0; i < ySteps; i++) {
            int tmp = x;
            x = z;
            z = -tmp;
        }
        for (int i = 0; i < xSteps; i++) {
            int tmp = y;
            y = -z;
            z = tmp;
        }
        for (int i = 0; i < zSteps; i++) {
            int tmp = x;
            x = y;
            y = -tmp;
        }
        return new int[] { x, y, z };
    }

    public static int directionToOrdinal(ForgeDirection direction) {
        return direction != null ? direction.ordinal() : 0;
    }

    public static ForgeDirection ordinalToDirection(int ordinal) {
        ForgeDirection[] values = ForgeDirection.VALID_DIRECTIONS;
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return ForgeDirection.UNKNOWN;
    }
}
