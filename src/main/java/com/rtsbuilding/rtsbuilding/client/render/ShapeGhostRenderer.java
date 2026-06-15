package com.rtsbuilding.rtsbuilding.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildSession;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.util.BlockPos;
import com.rtsbuilding.rtsbuilding.util.RtsUltimineCollector;

/**
 * 形状建造预览渲染器 — 根据选中形状计算方块位置列表，渲染半透明幽灵预览。
 * 对齐原版 ShapeGhostRenderer，支持 BLOCK/LINE/SQUARE/WALL/CIRCLE/BOX 六种形状。
 *
 * Bug #2 修复：当存在活跃的 ShapeBuildSession 时，基于锚点 (pointA/pointB) 生成预览，
 * 而非基于相机位置。支持圆柱模式 (CIRCLE + cylinder)。
 */
public class ShapeGhostRenderer {

    private static final int MAX_PREVIEW_DISTANCE = 80;
    private static final float BUILD_FILL_ALPHA = 0.25F;
    private static final float DESTROY_FILL_ALPHA = 0.22F;
    private static final int MAX_PREVIEW_BLOCKS = 2048;

    public ShapesRenderData computeShapes(RtsClientState state) {
        ShapesRenderData result = new ShapesRenderData();
        InteractionViewModel ivm = state.interaction;
        if (!ivm.quickBuildActive && !ivm.ultimineActive) return result;
        if (!state.camera.hasBounds()) return result;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null) return result;

        double cx = state.camera.posX;
        double cy = state.camera.posY;
        double cz = state.camera.posZ;
        double px = mc.renderViewEntity.posX;
        double py = mc.renderViewEntity.posY;
        double pz = mc.renderViewEntity.posZ;

        double dx = Math.abs(cx - px);
        double dy = Math.abs(cy - py);
        double dz = Math.abs(cz - pz);
        if (dx + dy + dz > MAX_PREVIEW_DISTANCE) return result;

        // 连锁挖掘模式：BFS 收集同类方块预览
        if (ivm.ultimineActive && !ivm.areaDestroyActive) {
            result.destructive = true;
            result.blocks = collectUltiminePreviewBlocks(mc, ivm);
            return result;
        }

        boolean destructive = ivm.areaDestroyActive;
        result.destructive = destructive;

        // Bug #2 修复：优先使用锚点会话生成预览
        ShapeBuildSession session = ivm.shapeBuildSession;
        if (session != null && session.pointA != null) {
            result.blocks = buildPreviewFromSession(session, ivm);
            return result;
        }

        // 修复: 无session时返回空预览(不再回退到相机位置)
        return result;
    }

    /**
     * Bug #2 修复：基于 ShapeBuildSession 的锚点生成预览方块列表。
     * 使用 ShapeGeometryUtil 的完整几何算法（含旋转、填充模式、圆柱支持）。
     */
    private List<BlockGhostPos> buildPreviewFromSession(ShapeBuildSession session, InteractionViewModel ivm) {
        List<BlockGhostPos> result = new ArrayList<>();

        // 解析填充模式
        ShapeFillMode fillMode = ShapeFillMode.parse(ivm.quickBuildFill);

        // P2-3: 当pointB未设置时，使用鼠标射线命中的位置作为临时pointB
        ShapeBuildSession effectiveSession = session;
        if (session.pointB == null
            && session.phase == com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildPhase.NEED_SECOND_POINT) {
            BlockPos mouseHit = getMouseHitBlockPos();
            if (mouseHit != null) {
                effectiveSession = new ShapeBuildSession(
                    session.shape,
                    session.pointA,
                    session.clickedFace,
                    session.rotationDegrees,
                    session.cylinder);
                effectiveSession.pointB = mouseHit;
                effectiveSession.heightOffset = session.heightOffset;
            }
        }

        // 使用几何引擎生成方块位置（含8向角度吸附）
        List<BlockPos> positions = ShapeGeometryUtil
            .buildShapePositions(effectiveSession, fillMode, ivm.lineSnap8Direction);

        // 转换为预览位置
        for (BlockPos pos : positions) {
            result.add(new BlockGhostPos(pos.getX(), pos.getY(), pos.getZ()));
            if (result.size() >= MAX_PREVIEW_BLOCKS) break;
        }

        return result;
    }

    /**
     * P2-3: 从当前鼠标位置做射线检测，返回命中的方块坐标。无命中返回null。
     */
    private BlockPos getMouseHitBlockPos() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;

        RtsCameraEntity camera = RtsClientState.get().camera.cameraEntity;
        if (camera == null) camera = RtsClientState.get().camera.localMirror;
        if (camera == null) return null;

        int displayMouseX = org.lwjgl.input.Mouse.getX();
        int displayMouseY = mc.displayHeight - org.lwjgl.input.Mouse.getY() - 1;
        double eyeY = camera.posY + camera.getEyeHeight();
        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);
        Vec3 look = RtsInteractionHandler.computeCursorRay(camera, displayMouseX, displayMouseY, mc);
        Vec3 end = start.addVector(look.xCoord * 200.0D, look.yCoord * 200.0D, look.zCoord * 200.0D);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return new com.rtsbuilding.rtsbuilding.util.BlockPos(hit.blockX, hit.blockY, hit.blockZ);
    }

    /**
     * 连锁挖掘预览：从玩家看向的方块出发，BFS 搜索相邻同类方块。
     */
    private List<BlockGhostPos> collectUltiminePreviewBlocks(Minecraft mc, InteractionViewModel ivm) {
        List<BlockGhostPos> result = new ArrayList<>();
        if (mc.theWorld == null) return result;

        // 修复: 使用鼠标光标射线而非相机视线
        RtsCameraEntity camera = RtsClientState.get().camera.cameraEntity;
        if (camera == null) camera = RtsClientState.get().camera.localMirror;
        if (camera == null) return result;

        int displayMouseX = org.lwjgl.input.Mouse.getX();
        int displayMouseY = mc.displayHeight - org.lwjgl.input.Mouse.getY() - 1;
        double eyeY = camera.posY + camera.getEyeHeight();
        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);
        Vec3 look = RtsInteractionHandler.computeCursorRay(camera, displayMouseX, displayMouseY, mc);
        double reach = 200.0D;
        Vec3 endPos = start.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, endPos);

        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return result;

        List<int[]> blocks = RtsUltimineCollector
            .collect(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ, ivm.ultimineLimit, 64);

        for (int[] pos : blocks) {
            result.add(new BlockGhostPos(pos[0], pos[1], pos[2]));
        }
        return result;
    }

    /**
     * 使用绝对世界坐标渲染（调用方已处理相机平移）。
     * P2-2: 连锁预览放大+黄线外轮廓
     */
    public void renderShapesAbs(ShapesRenderData data) {
        if (data.blocks.isEmpty()) return;

        if (data.destructive) {
            // Issue 3: 连锁破坏预览 — 明显大于正常方块 + 黄色不可遮挡外轮廓
            double off = -0.02; // 外扩0.02，预览方块=1.04倍正常方块
            for (BlockGhostPos pos : data.blocks) {
                double bx = pos.x + off;
                double by = pos.y + off;
                double bz = pos.z + off;
                double ex = pos.x + 1.0 - off;
                double ey = pos.y + 1.0 - off;
                double ez = pos.z + 1.0 - off;

                GL11.glColor4f(1.0F, 0.35F, 0.35F, DESTROY_FILL_ALPHA);
                GL11.glBegin(GL11.GL_QUADS);
                renderCube(bx, by, bz, ex, ey, ez);
                GL11.glEnd();
            }

            // 黄色不可遮挡外轮廓线
            renderUltimineOutline(data.blocks);
        } else {
            // 建造预览 — 原有逻辑
            for (BlockGhostPos pos : data.blocks) {
                double bx = pos.x + 0.03;
                double by = pos.y + 0.03;
                double bz = pos.z + 0.03;
                double ex = pos.x + 0.97;
                double ey = pos.y + 0.97;
                double ez = pos.z + 0.97;

                GL11.glColor4f(0.3F, 0.7F, 1.0F, BUILD_FILL_ALPHA);
                GL11.glBegin(GL11.GL_QUADS);
                renderCube(bx, by, bz, ex, ey, ez);
                GL11.glEnd();

                GL11.glColor4f(0.35F, 0.75F, 1.0F, 0.8F);
                GL11.glLineWidth(1.2F);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex3d(bx, ey, bz);
                GL11.glVertex3d(ex, ey, bz);
                GL11.glVertex3d(ex, ey, ez);
                GL11.glVertex3d(bx, ey, ez);
                GL11.glEnd();
            }
        }

        GL11.glLineWidth(1.0F);
    }

    /**
     * 连锁破坏黄色外轮廓线 — 沿连锁形状的边界轮廓。
     * 剔除两个可见面共享的边，只保留外边界边，合并共线相邻线段。
     */
    private void renderUltimineOutline(List<BlockGhostPos> blocks) {
        java.util.Set<Long> blockSet = new java.util.HashSet<>();
        for (BlockGhostPos pos : blocks) blockSet.add(blockHash(pos.x, pos.y, pos.z));

        java.util.Map<String, java.util.Set<Long>> edgeBlocks = new java.util.HashMap<>();
        for (BlockGhostPos pos : blocks) {
            int x = pos.x, y = pos.y, z = pos.z;
            long bid = blockHash(x, y, z);
            if (!blockSet.contains(blockHash(x, y + 1, z))) addFaceEdges(edgeBlocks, x, y, z, 0, bid);
            if (!blockSet.contains(blockHash(x, y - 1, z))) addFaceEdges(edgeBlocks, x, y, z, 1, bid);
            if (!blockSet.contains(blockHash(x + 1, y, z))) addFaceEdges(edgeBlocks, x, y, z, 2, bid);
            if (!blockSet.contains(blockHash(x - 1, y, z))) addFaceEdges(edgeBlocks, x, y, z, 3, bid);
            if (!blockSet.contains(blockHash(x, y, z + 1))) addFaceEdges(edgeBlocks, x, y, z, 4, bid);
            if (!blockSet.contains(blockHash(x, y, z - 1))) addFaceEdges(edgeBlocks, x, y, z, 5, bid);
        }

        java.util.List<int[]> boundaryEdges = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Set<Long>> entry : edgeBlocks.entrySet()) {
            if (entry.getValue()
                .size() == 1) boundaryEdges.add(parseEdgeKey(entry.getKey()));
        }

        java.util.List<int[]> merged = mergeCollinearSegments(boundaryEdges);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 0.0F, 1.0F);
        GL11.glLineWidth(2.5F);
        GL11.glBegin(GL11.GL_LINES);
        for (int[] edge : merged) {
            GL11.glVertex3d(edge[0], edge[1], edge[2]);
            GL11.glVertex3d(edge[3], edge[4], edge[5]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void addFaceEdges(java.util.Map<String, java.util.Set<Long>> eb, int x, int y, int z, int face, long bid) {
        int[][] edges;
        switch (face) {
            case 0:
                edges = new int[][] { { x, y + 1, z, x + 1, y + 1, z }, { x + 1, y + 1, z, x + 1, y + 1, z + 1 },
                    { x + 1, y + 1, z + 1, x, y + 1, z + 1 }, { x, y + 1, z + 1, x, y + 1, z } };
                break;
            case 1:
                edges = new int[][] { { x, y, z, x + 1, y, z }, { x + 1, y, z, x + 1, y, z + 1 },
                    { x + 1, y, z + 1, x, y, z + 1 }, { x, y, z + 1, x, y, z } };
                break;
            case 2:
                edges = new int[][] { { x + 1, y, z, x + 1, y, z + 1 }, { x + 1, y, z + 1, x + 1, y + 1, z + 1 },
                    { x + 1, y + 1, z + 1, x + 1, y + 1, z }, { x + 1, y + 1, z, x + 1, y, z } };
                break;
            case 3:
                edges = new int[][] { { x, y, z, x, y, z + 1 }, { x, y, z + 1, x, y + 1, z + 1 },
                    { x, y + 1, z + 1, x, y + 1, z }, { x, y + 1, z, x, y, z } };
                break;
            case 4:
                edges = new int[][] { { x, y, z + 1, x + 1, y, z + 1 }, { x + 1, y, z + 1, x + 1, y + 1, z + 1 },
                    { x + 1, y + 1, z + 1, x, y + 1, z + 1 }, { x, y + 1, z + 1, x, y, z + 1 } };
                break;
            case 5:
                edges = new int[][] { { x, y, z, x + 1, y, z }, { x + 1, y, z, x + 1, y + 1, z },
                    { x + 1, y + 1, z, x, y + 1, z }, { x, y + 1, z, x, y, z } };
                break;
            default:
                return;
        }
        for (int[] e : edges) eb.computeIfAbsent(edgeKey(e), k -> new java.util.HashSet<>())
            .add(bid);
    }

    private int[] parseEdgeKey(String key) {
        String[] p = key.split(",");
        return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
            Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]) };
    }

    private String edgeKey(int[] e) {
        if (e[0] < e[3] || (e[0] == e[3] && e[1] < e[4]) || (e[0] == e[3] && e[1] == e[4] && e[2] < e[5])) {
            return e[0] + "," + e[1] + "," + e[2] + "," + e[3] + "," + e[4] + "," + e[5];
        }
        return e[3] + "," + e[4] + "," + e[5] + "," + e[0] + "," + e[1] + "," + e[2];
    }

    private long blockHash(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | ((long) y & 0x3FFFFFFL) << 26 | ((long) z & 0x3FFFFFFL) << 52;
    }

    private java.util.List<int[]> mergeCollinearSegments(java.util.List<int[]> edges) {
        if (edges.size() <= 1) return new java.util.ArrayList<>(edges);
        java.util.Map<String, java.util.List<int[]>> groups = new java.util.HashMap<>();
        for (int[] e : edges) {
            int dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2];
            groups
                .computeIfAbsent(
                    dx != 0 ? "X:" + e[1] + ":" + e[2] : dy != 0 ? "Y:" + e[0] + ":" + e[2] : "Z:" + e[0] + ":" + e[1],
                    k -> new java.util.ArrayList<>())
                .add(e);
        }
        java.util.List<int[]> result = new java.util.ArrayList<>();
        for (java.util.List<int[]> group : groups.values()) {
            group.sort((a, b) -> {
                for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
                return 0;
            });
            int[] cur = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                int[] next = group.get(i);
                if (cur[3] == next[0] && cur[4] == next[1] && cur[5] == next[2]) {
                    cur[3] = next[3];
                    cur[4] = next[4];
                    cur[5] = next[5];
                } else {
                    result.add(cur);
                    cur = next;
                }
            }
            result.add(cur);
        }
        return result;
    }

    private static void renderCube(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y2, z2);

        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z1);

        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y2, z2);

        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1);

        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z1);

        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    /**
     * 回退方案：根据形状枚举计算预览方块位置列表（基于中心点）。
     */
    public static List<BlockGhostPos> calculateBlocks(BuildShape shape, int cx, int cy, int cz, int sx, int sy, int sz,
        boolean destructive) {
        List<BlockGhostPos> blocks = new ArrayList<>();
        int halfX = Math.max(0, (sx - 1) / 2);
        int halfY = Math.max(0, (sy - 1) / 2);
        int halfZ = Math.max(0, (sz - 1) / 2);

        switch (shape) {
            case BLOCK:
                for (int ddx = 0; ddx < sx; ddx++) for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++)
                    blocks.add(new BlockGhostPos(cx - halfX + ddx, cy + dy, cz - halfZ + dz));
                break;
            case LINE:
                int count = Math.max(sx, Math.max(sy, sz));
                for (int i = 0; i < count; i++) {
                    int ddx = sx > 1 ? i : 0;
                    int dy = sy > 1 ? i : 0;
                    int dz = sz > 1 ? i : 0;
                    blocks.add(new BlockGhostPos(cx + ddx, cy + dy, cz + dz));
                }
                break;
            case SQUARE:
                for (int ddx = 0; ddx < sx; ddx++) for (int dz = 0; dz < sz; dz++)
                    blocks.add(new BlockGhostPos(cx - halfX + ddx, cy, cz - halfZ + dz));
                break;
            case WALL:
                for (int ddx = 0; ddx < sx; ddx++) for (int dy = 0; dy < sy; dy++) {
                    blocks.add(new BlockGhostPos(cx - halfX + ddx, cy + dy, cz));
                    blocks.add(new BlockGhostPos(cx - halfX + ddx, cy + dy, cz + sz - 1));
                }
                for (int dz = 1; dz < sz - 1; dz++) for (int dy = 0; dy < sy; dy++) {
                    blocks.add(new BlockGhostPos(cx - halfX, cy + dy, cz + dz));
                    blocks.add(new BlockGhostPos(cx - halfX + sx - 1, cy + dy, cz + dz));
                }
                break;
            case CIRCLE:
                double radius = Math.max(sx, sz) / 2.0;
                for (int ddx = -halfX; ddx <= halfX; ddx++)
                    for (int dz = -halfZ; dz <= halfZ; dz++) if (ddx * ddx + dz * dz <= radius * radius + 0.5)
                        for (int dy = 0; dy < sy; dy++) blocks.add(new BlockGhostPos(cx + ddx, cy + dy, cz + dz));
                break;
            case BOX:
                for (int ddx = 0; ddx < sx; ddx++) for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++)
                    if (ddx == 0 || ddx == sx - 1 || dy == 0 || dy == sy - 1 || dz == 0 || dz == sz - 1)
                        blocks.add(new BlockGhostPos(cx - halfX + ddx, cy + dy, cz - halfZ + dz));
                break;
        }

        if (blocks.size() > MAX_PREVIEW_DISTANCE * 10) blocks.clear();
        return blocks;
    }

    private static BuildShape parseShape(String shapeName) {
        if (shapeName == null) return BuildShape.BLOCK;
        String s = shapeName.toUpperCase()
            .trim();
        switch (s) {
            case "LINE":
                return BuildShape.LINE;
            case "SQUARE":
                return BuildShape.SQUARE;
            case "WALL":
                return BuildShape.WALL;
            case "CIRCLE":
                return BuildShape.CIRCLE;
            case "BOX":
                return BuildShape.BOX;
            default:
                return BuildShape.BLOCK;
        }
    }

    public static class ShapesRenderData {

        public List<BlockGhostPos> blocks = new ArrayList<>();
        public boolean destructive;
    }

    public static class BlockGhostPos {

        public final int x, y, z;

        BlockGhostPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
