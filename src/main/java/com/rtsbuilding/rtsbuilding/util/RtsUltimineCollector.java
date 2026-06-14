package com.rtsbuilding.rtsbuilding.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * 连锁挖掘 BFS 收集器 — 从种子方块出发，广度优先搜索相邻同类型方块。
 * 用于客户端预览和服务端目标收集。
 */
public class RtsUltimineCollector {

    private static final int[][] DIRECTIONS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    /**
     * 从种子方块出发 BFS 收集相邻同类型方块。
     *
     * @param world     世界
     * @param seedX     种子方块 X
     * @param seedY     种子方块 Y
     * @param seedZ     种子方块 Z
     * @param limit     最大收集数量
     * @param maxRadius 最大搜索半径（曼哈顿距离）
     * @return 收集到的方块坐标列表 [x, y, z]
     */
    public static List<int[]> collect(World world, int seedX, int seedY, int seedZ, int limit, int maxRadius) {
        if (world == null || limit <= 0) return Collections.emptyList();

        Block seedBlock = world.getBlock(seedX, seedY, seedZ);
        if (seedBlock == null || world.isAirBlock(seedX, seedY, seedZ)) return Collections.emptyList();
        int seedMeta = world.getBlockMetadata(seedX, seedY, seedZ);

        int clampedLimit = Math.min(Math.max(1, limit), 256);
        int clampedRadius = Math.max(1, maxRadius);

        List<int[]> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<int[]> frontier = new ArrayDeque<>();

        long seedHash = hash(seedX, seedY, seedZ);
        visited.add(seedHash);
        frontier.addLast(new int[] { seedX, seedY, seedZ });

        while (!frontier.isEmpty() && result.size() < clampedLimit) {
            int[] current = frontier.removeFirst();
            int x = current[0], y = current[1], z = current[2];

            // 曼哈顿距离限制
            if (manhattanDistance(seedX, seedY, seedZ, x, y, z) > clampedRadius) continue;

            // 验证：非空气、类型匹配、可破坏
            if (world.isAirBlock(x, y, z)) continue;
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (block != seedBlock || meta != seedMeta) continue;
            if (block.getBlockHardness(world, x, y, z) < 0) continue;

            result.add(current);

            // 向 6 个方向扩展
            for (int[] dir : DIRECTIONS) {
                int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
                long key = hash(nx, ny, nz);
                if (visited.contains(key)) continue;
                if (!world.blockExists(nx, ny, nz)) continue;
                visited.add(key);
                frontier.addLast(new int[] { nx, ny, nz });
            }
        }

        // 排序：先按距离平方，再按 Y, X, Z
        final int sX = seedX, sY = seedY, sZ = seedZ;
        Collections.sort(result, new Comparator<int[]>() {

            @Override
            public int compare(int[] a, int[] b) {
                long dA = distanceSquared(sX, sY, sZ, a[0], a[1], a[2]);
                long dB = distanceSquared(sX, sY, sZ, b[0], b[1], b[2]);
                int cmp = Long.compare(dA, dB);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a[1], b[1]);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a[0], b[0]);
                if (cmp != 0) return cmp;
                return Integer.compare(a[2], b[2]);
            }
        });

        return result;
    }

    private static long hash(int x, int y, int z) {
        return ((long) x & 0x1FFFFFFFL) | (((long) y & 0xFFFFFL) << 29) | (((long) z & 0x1FFFFFFFL) << 50);
    }

    private static int manhattanDistance(int x1, int y1, int z1, int x2, int y2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2);
    }

    private static long distanceSquared(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        long dz = (long) z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
