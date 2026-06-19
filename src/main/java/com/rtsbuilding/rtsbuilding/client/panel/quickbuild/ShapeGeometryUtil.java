package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.common.BuildShape;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 快速建造形状几何算法，移植自原版 ShapeGeometryUtil 并适配 1.7.10。
 */
public final class ShapeGeometryUtil {

    public static final int SHAPE_MAX_OFFSET = 31;
    public static final int SHAPE_MAX_RADIUS = 32;
    public static final int SHAPE_ROTATE_STEP_DEGREES = 15;

    public static List<BlockPos> buildShapePositions(ShapeBuildSession session, ShapeFillMode fillMode,
        boolean lineSnap8Dir) {
        if (session == null || session.pointA == null) return new ArrayList<>();
        BlockPos pointB = session.pointB != null ? session.pointB : session.pointA;
        return buildShapePositions(
            session.shape,
            session.clickedFace,
            session.pointA,
            pointB,
            session.heightOffset,
            session.rotationDegrees,
            session.cylinder,
            fillMode,
            lineSnap8Dir);
    }

    public static List<BlockPos> buildShapePositions(BuildShape shape, int clickedFace, BlockPos start, BlockPos end,
        int heightOffset, int rotationDegrees, boolean cylinder, ShapeFillMode fillMode, boolean lineSnap8Dir) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        if (shape == null) shape = BuildShape.BLOCK;
        if (fillMode == null) fillMode = ShapeFillMode.FILL;

        switch (shape) {
            case LINE:
                addLineTargets(targets, start, end, lineSnap8Dir);
                break;
            case SQUARE:
                addSquareTargets(targets, start, end, ForgeDirection.UP, fillMode, rotationDegrees);
                break;
            case WALL:
                addWallTargets(targets, start, end, heightOffset, fillMode, lineSnap8Dir);
                break;
            case CIRCLE:
                if (cylinder) {
                    addCylinderTargets(targets, start, end, heightOffset, fillMode, rotationDegrees);
                } else {
                    addCircleTargets(targets, start, end, getDirection(clickedFace), fillMode, rotationDegrees);
                }
                break;
            case BOX:
                addBoxTargets(targets, start, end, heightOffset, fillMode, rotationDegrees);
                break;
            case BLOCK:
            default:
                targets.add(start);
                break;
        }
        return new ArrayList<>(targets);
    }

    public static ShapeFillMode[] availableFillModes(BuildShape shape) {
        if (shape == null) return new ShapeFillMode[] { ShapeFillMode.FILL };
        switch (shape) {
            case SQUARE:
            case WALL:
            case CIRCLE:
                return new ShapeFillMode[] { ShapeFillMode.FILL, ShapeFillMode.HOLLOW };
            case BOX:
                return new ShapeFillMode[] { ShapeFillMode.FILL, ShapeFillMode.HOLLOW, ShapeFillMode.SKELETON };
            case LINE:
            case BLOCK:
            default:
                return new ShapeFillMode[] { ShapeFillMode.FILL };
        }
    }

    public static boolean isFillModeAllowed(BuildShape shape, ShapeFillMode fillMode) {
        ShapeFillMode[] modes = availableFillModes(shape);
        for (ShapeFillMode mode : modes) {
            if (mode == fillMode) return true;
        }
        return false;
    }

    public static boolean requiresHeight(BuildShape shape, boolean cylinder) {
        return shape == BuildShape.WALL || shape == BuildShape.BOX || (shape == BuildShape.CIRCLE && cylinder);
    }

    public static int clampShapeOffset(int value) {
        return Math.max(-SHAPE_MAX_OFFSET, Math.min(SHAPE_MAX_OFFSET, value));
    }

    public static int normalizeRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return (normalized / SHAPE_ROTATE_STEP_DEGREES) * SHAPE_ROTATE_STEP_DEGREES;
    }

    private static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, boolean snap8Dir) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();

        // P1-1: 8向角度吸附（22.5°阈值，实时吸附）
        if (snap8Dir && (dx != 0 || dz != 0)) {
            double angle = Math.atan2(dz, dx); // [-PI, PI]
            double deg = Math.toDegrees(angle); // [-180, 180]
            double snapped = Math.round(deg / 45.0) * 45.0;
            double rad = Math.toRadians(snapped);
            double dist = Math.sqrt(dx * dx + dz * dz);
            dx = (int) Math.round(Math.cos(rad) * dist);
            dz = (int) Math.round(Math.sin(rad) * dist);
        }

        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 0) {
            targets.add(start);
            return;
        }
        if (steps > SHAPE_MAX_OFFSET) {
            double scale = SHAPE_MAX_OFFSET / (double) steps;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
            dz = (int) Math.round(dz * scale);
            steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            targets.add(
                new BlockPos(
                    start.getX() + (int) Math.round(dx * t),
                    start.getY() + (int) Math.round(dy * t),
                    start.getZ() + (int) Math.round(dz * t)));
        }
    }

    private static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, ForgeDirection face,
        ShapeFillMode fillMode, int degrees) {
        ForgeDirection[] axes = resolveShapePlaneAxes(BuildShape.SQUARE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]));
        addRotatedPlaneRectangleTargets(targets, start, axes[0], axes[1], aOffset, bOffset, fillMode, degrees);
    }

    private static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
        ShapeFillMode fillMode, boolean lineSnap8Dir) {
        LinkedHashSet<BlockPos> baseLine = new LinkedHashSet<>();
        // WALL的底边线使用与LINE相同的8向吸附逻辑
        BlockPos wallEnd = new BlockPos(end.getX(), start.getY(), end.getZ());
        addLineTargets(baseLine, start, wallEnd, lineSnap8Dir);
        if (baseLine.isEmpty()) baseLine.add(start);

        int yOffset = clampShapeOffset(heightOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        List<BlockPos> base = new ArrayList<>(baseLine);
        for (int i = 0; i < base.size(); i++) {
            BlockPos basePos = base.get(i);
            boolean endColumn = i == 0 || i == base.size() - 1;
            for (int iy = minY; iy <= maxY; iy++) {
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) continue;
                targets.add(new BlockPos(basePos.getX(), basePos.getY() + iy, basePos.getZ()));
            }
        }
    }

    private static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, ForgeDirection face,
        ShapeFillMode fillMode, int degrees) {
        ForgeDirection[] axes = resolveShapePlaneAxes(BuildShape.CIRCLE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = Math
            .max(0, Math.min(SHAPE_MAX_RADIUS, (int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b)))));
        Set<PlaneCell> cells = buildCircleCells(radius, fillMode, degrees);
        for (PlaneCell cell : cells) {
            targets.add(offsetPos(start, axes[0], cell.a, axes[1], cell.b));
        }
    }

    private static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
        ShapeFillMode fillMode, int degrees) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int radius = Math
            .max(0, Math.min(SHAPE_MAX_RADIUS, (int) Math.round(Math.sqrt((dx * (double) dx) + (dz * (double) dz)))));
        int yOffset = clampShapeOffset(heightOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        Set<PlaneCell> fillCells = buildCircleCells(radius, ShapeFillMode.FILL, degrees);
        Set<PlaneCell> ringCells = buildCircleCells(radius, ShapeFillMode.HOLLOW, degrees);
        for (int iy = minY; iy <= maxY; iy++) {
            for (PlaneCell cell : fillCells) {
                boolean boundary = ringCells.contains(cell);
                if (fillMode == ShapeFillMode.FILL || boundary || iy == minY || iy == maxY) {
                    targets.add(new BlockPos(start.getX() + cell.a, start.getY() + iy, start.getZ() + cell.b));
                }
            }
        }
    }

    private static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
        ShapeFillMode fillMode, int degrees) {
        int xOffset = clampShapeOffset(end.getX() - start.getX());
        int zOffset = clampShapeOffset(end.getZ() - start.getZ());
        int yOffset = clampShapeOffset(heightOffset);
        int minX = Math.min(0, xOffset);
        int maxX = Math.max(0, xOffset);
        int minZ = Math.min(0, zOffset);
        int maxZ = Math.max(0, zOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        Set<PlaneCell> footprint = buildRotatedRectangleFillCells(minX, maxX, minZ, maxZ, degrees);
        if (fillMode == ShapeFillMode.FILL) {
            for (PlaneCell cell : footprint) {
                for (int iy = minY; iy <= maxY; iy++) {
                    targets.add(new BlockPos(start.getX() + cell.a, start.getY() + iy, start.getZ() + cell.b));
                }
            }
            return;
        }

        Set<BlockPos> fullVolume = new HashSet<>();
        for (PlaneCell cell : footprint) {
            for (int iy = minY; iy <= maxY; iy++) {
                fullVolume.add(new BlockPos(start.getX() + cell.a, start.getY() + iy, start.getZ() + cell.b));
            }
        }
        for (BlockPos pos : fullVolume) {
            boolean xBoundary = !fullVolume.contains(new BlockPos(pos.getX() + 1, pos.getY(), pos.getZ()))
                || !fullVolume.contains(new BlockPos(pos.getX() - 1, pos.getY(), pos.getZ()));
            boolean yBoundary = !fullVolume.contains(new BlockPos(pos.getX(), pos.getY() + 1, pos.getZ()))
                || !fullVolume.contains(new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ()));
            boolean zBoundary = !fullVolume.contains(new BlockPos(pos.getX(), pos.getY(), pos.getZ() + 1))
                || !fullVolume.contains(new BlockPos(pos.getX(), pos.getY(), pos.getZ() - 1));
            int boundaryAxes = (xBoundary ? 1 : 0) + (yBoundary ? 1 : 0) + (zBoundary ? 1 : 0);
            if (fillMode == ShapeFillMode.HOLLOW && boundaryAxes >= 1) targets.add(pos);
            if (fillMode == ShapeFillMode.SKELETON && boundaryAxes >= 2) targets.add(pos);
        }
    }

    private static Set<PlaneCell> buildCircleCells(int radius, ShapeFillMode fillMode, int degrees) {
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        Set<PlaneCell> cells = new HashSet<>();
        for (int ia = -radius; ia <= radius; ia++) {
            for (int ib = -radius; ib <= radius; ib++) {
                int dist2 = (ia * ia) + (ib * ib);
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                if (!inOuter || (fillMode != ShapeFillMode.FILL && inInner)) continue;
                RotatedOffset rotated = rotatePlaneOffset(ia, ib, 0.0D, 0.0D, degrees);
                cells.add(new PlaneCell(rotated.a, rotated.b));
            }
        }
        return fillMode == ShapeFillMode.FILL ? fillPlaneInteriorHoles(cells) : cells;
    }

    private static void addRotatedPlaneRectangleTargets(Set<BlockPos> targets, BlockPos start, ForgeDirection axisA,
        ForgeDirection axisB, int aOffset, int bOffset, ShapeFillMode fillMode, int degrees) {
        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);
        Set<PlaneCell> cells = buildRotatedRectangleFillCells(minA, maxA, minB, maxB, degrees);
        for (PlaneCell cell : cells) {
            if (fillMode == ShapeFillMode.FILL || isPlaneBoundaryCell(cells, cell)) {
                targets.add(offsetPos(start, axisA, cell.a, axisB, cell.b));
            }
        }
    }

    private static boolean isPlaneBoundaryCell(Set<PlaneCell> cells, PlaneCell cell) {
        return !cells.contains(new PlaneCell(cell.a + 1, cell.b)) || !cells.contains(new PlaneCell(cell.a - 1, cell.b))
            || !cells.contains(new PlaneCell(cell.a, cell.b + 1))
            || !cells.contains(new PlaneCell(cell.a, cell.b - 1));
    }

    private static Set<PlaneCell> buildRotatedRectangleFillCells(int minA, int maxA, int minB, int maxB, int degrees) {
        Set<PlaneCell> filled = new HashSet<>();
        int normalized = normalizeRotation(degrees);
        if (normalized == 0) {
            for (int a = minA; a <= maxA; a++) for (int b = minB; b <= maxB; b++) filled.add(new PlaneCell(a, b));
            return fillPlaneInteriorHoles(filled);
        }

        double centerA = (minA + maxA) * 0.5D;
        double centerB = (minB + maxB) * 0.5D;
        double rad = Math.toRadians(normalized);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double[][] corners = { { minA, minB }, { minA, maxB }, { maxA, minB }, { maxA, maxB } };
        double minRotA = Double.POSITIVE_INFINITY;
        double maxRotA = Double.NEGATIVE_INFINITY;
        double minRotB = Double.POSITIVE_INFINITY;
        double maxRotB = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double da = corner[0] - centerA;
            double db = corner[1] - centerB;
            double ra = (da * cos) - (db * sin) + centerA;
            double rb = (da * sin) + (db * cos) + centerB;
            minRotA = Math.min(minRotA, ra);
            maxRotA = Math.max(maxRotA, ra);
            minRotB = Math.min(minRotB, rb);
            maxRotB = Math.max(maxRotB, rb);
        }
        for (int a = (int) Math.floor(minRotA) - 1; a <= (int) Math.ceil(maxRotA) + 1; a++) {
            for (int b = (int) Math.floor(minRotB) - 1; b <= (int) Math.ceil(maxRotB) + 1; b++) {
                if (isInverseRotatedInsideCellBounds(a, b, minA, maxA, minB, maxB, centerA, centerB, cos, sin)) {
                    filled.add(new PlaneCell(a, b));
                }
            }
        }
        return fillPlaneInteriorHoles(filled);
    }

    private static boolean isInverseRotatedInsideCellBounds(int targetA, int targetB, int minA, int maxA, int minB,
        int maxB, double centerA, double centerB, double cos, double sin) {
        double[][] samples = { { 0.0D, 0.0D }, { -0.35D, 0.0D }, { 0.35D, 0.0D }, { 0.0D, -0.35D }, { 0.0D, 0.35D },
            { -0.3D, -0.3D }, { -0.3D, 0.3D }, { 0.3D, -0.3D }, { 0.3D, 0.3D } };
        for (double[] sample : samples) {
            double da = (targetA + sample[0]) - centerA;
            double db = (targetB + sample[1]) - centerB;
            double sourceA = (da * cos) + (db * sin) + centerA;
            double sourceB = (-da * sin) + (db * cos) + centerB;
            if (sourceA >= minA - 0.5D && sourceA <= maxA + 0.5D && sourceB >= minB - 0.5D && sourceB <= maxB + 0.5D) {
                return true;
            }
        }
        return false;
    }

    private static Set<PlaneCell> fillPlaneInteriorHoles(Set<PlaneCell> filledCells) {
        if (filledCells == null || filledCells.isEmpty()) return new HashSet<>();
        int minA = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE;
        int maxB = Integer.MIN_VALUE;
        for (PlaneCell cell : filledCells) {
            minA = Math.min(minA, cell.a);
            maxA = Math.max(maxA, cell.a);
            minB = Math.min(minB, cell.b);
            maxB = Math.max(maxB, cell.b);
        }
        int extMinA = minA - 1;
        int extMaxA = maxA + 1;
        int extMinB = minB - 1;
        int extMaxB = maxB + 1;
        Set<PlaneCell> outside = new HashSet<>();
        ArrayDeque<PlaneCell> queue = new ArrayDeque<>();
        for (int a = extMinA; a <= extMaxA; a++) {
            queueOutside(new PlaneCell(a, extMinB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutside(new PlaneCell(a, extMaxB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }
        for (int b = extMinB + 1; b <= extMaxB - 1; b++) {
            queueOutside(new PlaneCell(extMinA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutside(new PlaneCell(extMaxA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }
        while (!queue.isEmpty()) {
            PlaneCell cell = queue.removeFirst();
            queueOutside(
                new PlaneCell(cell.a + 1, cell.b),
                filledCells,
                outside,
                queue,
                extMinA,
                extMaxA,
                extMinB,
                extMaxB);
            queueOutside(
                new PlaneCell(cell.a - 1, cell.b),
                filledCells,
                outside,
                queue,
                extMinA,
                extMaxA,
                extMinB,
                extMaxB);
            queueOutside(
                new PlaneCell(cell.a, cell.b + 1),
                filledCells,
                outside,
                queue,
                extMinA,
                extMaxA,
                extMinB,
                extMaxB);
            queueOutside(
                new PlaneCell(cell.a, cell.b - 1),
                filledCells,
                outside,
                queue,
                extMinA,
                extMaxA,
                extMinB,
                extMaxB);
        }
        Set<PlaneCell> dense = new HashSet<>(filledCells);
        for (int a = minA; a <= maxA; a++) for (int b = minB; b <= maxB; b++) {
            PlaneCell cell = new PlaneCell(a, b);
            if (!dense.contains(cell) && !outside.contains(cell)) dense.add(cell);
        }
        return dense;
    }

    private static void queueOutside(PlaneCell cell, Set<PlaneCell> filledCells, Set<PlaneCell> outside,
        ArrayDeque<PlaneCell> queue, int minA, int maxA, int minB, int maxB) {
        if (cell.a < minA || cell.a > maxA || cell.b < minB || cell.b > maxB) return;
        if (filledCells.contains(cell) || outside.contains(cell)) return;
        outside.add(cell);
        queue.addLast(cell);
    }

    private static int dotDelta(int dx, int dy, int dz, ForgeDirection axis) {
        return (dx * axis.offsetX) + (dy * axis.offsetY) + (dz * axis.offsetZ);
    }

    private static BlockPos offsetPos(BlockPos origin, ForgeDirection axisA, int stepA, ForgeDirection axisB,
        int stepB) {
        return new BlockPos(
            origin.getX() + (axisA.offsetX * stepA) + (axisB.offsetX * stepB),
            origin.getY() + (axisA.offsetY * stepA) + (axisB.offsetY * stepB),
            origin.getZ() + (axisA.offsetZ * stepA) + (axisB.offsetZ * stepB));
    }

    private static RotatedOffset rotatePlaneOffset(int a, int b, double centerA, double centerB, int degrees) {
        int normalized = normalizeRotation(degrees);
        if (normalized == 0) return new RotatedOffset(a, b);
        double rad = Math.toRadians(normalized);
        double da = a - centerA;
        double db = b - centerB;
        return new RotatedOffset(
            (int) Math.round((da * Math.cos(rad)) - (db * Math.sin(rad)) + centerA),
            (int) Math.round((da * Math.sin(rad)) + (db * Math.cos(rad)) + centerB));
    }

    private static ForgeDirection[] resolveShapePlaneAxes(BuildShape shape, ForgeDirection face) {
        if (shape == BuildShape.SQUARE || shape == BuildShape.BOX || shape == BuildShape.WALL) {
            return new ForgeDirection[] { ForgeDirection.EAST, ForgeDirection.SOUTH };
        }
        if (face == ForgeDirection.EAST || face == ForgeDirection.WEST) {
            return new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.SOUTH };
        }
        if (face == ForgeDirection.NORTH || face == ForgeDirection.SOUTH) {
            return new ForgeDirection[] { ForgeDirection.EAST, ForgeDirection.UP };
        }
        return new ForgeDirection[] { ForgeDirection.EAST, ForgeDirection.SOUTH };
    }

    private static ForgeDirection getDirection(int side) {
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        return direction == ForgeDirection.UNKNOWN ? ForgeDirection.UP : direction;
    }

    private static final class RotatedOffset {

        final int a;
        final int b;

        RotatedOffset(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    private static final class PlaneCell {

        final int a;
        final int b;

        PlaneCell(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PlaneCell)) return false;
            PlaneCell other = (PlaneCell) obj;
            return a == other.a && b == other.b;
        }

        @Override
        public int hashCode() {
            return 31 * a + b;
        }
    }

    private ShapeGeometryUtil() {}
}
