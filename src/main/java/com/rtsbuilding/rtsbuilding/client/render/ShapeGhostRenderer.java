package com.rtsbuilding.rtsbuilding.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.util.RtsUltimineCollector;

/**
 * 形状建造预览渲染器 — 根据选中形状计算方块位置列表，渲染半透明幽灵预览。
 * 对齐原版 ShapeGhostRenderer，支持 BLOCK/LINE/SQUARE/WALL/CIRCLE/BOX 六种形状。
 */
public class ShapeGhostRenderer {

    private static final int MAX_PREVIEW_DISTANCE = 80;
    private static final float BUILD_FILL_ALPHA = 0.25F;
    private static final float DESTROY_FILL_ALPHA = 0.22F;

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

        // 快速建造/范围破坏模式：使用形状系统
        BuildShape shape = parseShape(ivm.quickBuildShape);
        int sx = ivm.quickBuildSizeX;
        int sy = ivm.quickBuildSizeY;
        int sz = ivm.quickBuildSizeZ;

        boolean destructive = ivm.areaDestroyActive || (ivm.ultimineActive && ivm.areaDestroyActive);
        result.destructive = destructive;
        result.blocks = calculateBlocks(shape, (int) cx, (int) cy - 1, (int) cz, sx, sy, sz, destructive);
        return result;
    }

    /**
     * 连锁挖掘预览：从玩家看向的方块出发，BFS 搜索相邻同类方块。
     */
    private List<BlockGhostPos> collectUltiminePreviewBlocks(Minecraft mc, InteractionViewModel ivm) {
        List<BlockGhostPos> result = new ArrayList<>();
        if (mc.theWorld == null || mc.renderViewEntity == null) return result;

        Vec3 eyePos = mc.renderViewEntity.getPosition(1.0F);
        Vec3 lookVec = mc.renderViewEntity.getLook(1.0F);
        double reach = 200.0D;
        Vec3 endPos = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyePos, endPos);

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
     */
    public void renderShapesAbs(ShapesRenderData data) {
        if (data.blocks.isEmpty()) return;

        for (BlockGhostPos pos : data.blocks) {
            double bx = pos.x + 0.03;
            double by = pos.y + 0.03;
            double bz = pos.z + 0.03;
            double ex = pos.x + 0.97;
            double ey = pos.y + 0.97;
            double ez = pos.z + 0.97;

            if (data.destructive) {
                GL11.glColor4f(1.0F, 0.35F, 0.35F, DESTROY_FILL_ALPHA);
            } else {
                GL11.glColor4f(0.3F, 0.7F, 1.0F, BUILD_FILL_ALPHA);
            }

            GL11.glBegin(GL11.GL_QUADS);
            renderCube(bx, by, bz, ex, ey, ez);
            GL11.glEnd();

            if (data.destructive) {
                GL11.glColor4f(1.0F, 0.45F, 0.45F, 0.8F);
            } else {
                GL11.glColor4f(0.35F, 0.75F, 1.0F, 0.8F);
            }
            GL11.glLineWidth(1.2F);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(bx, ey, bz);
            GL11.glVertex3d(ex, ey, bz);
            GL11.glVertex3d(ex, ey, ez);
            GL11.glVertex3d(bx, ey, ez);
            GL11.glEnd();
        }

        GL11.glLineWidth(1.0F);
    }

    private void renderCube(double x1, double y1, double z1, double x2, double y2, double z2) {
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
     * 根据形状枚举计算预览方块位置列表。
     */
    public static List<BlockGhostPos> calculateBlocks(BuildShape shape, int cx, int cy, int cz, int sx, int sy, int sz,
        boolean destructive) {
        List<BlockGhostPos> blocks = new ArrayList<>();
        int halfX = Math.max(0, (sx - 1) / 2);
        int halfY = Math.max(0, (sy - 1) / 2);
        int halfZ = Math.max(0, (sz - 1) / 2);

        switch (shape) {
            case BLOCK:
                for (int dx = 0; dx < sx; dx++) for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++)
                    blocks.add(new BlockGhostPos(cx - halfX + dx, cy + dy, cz - halfZ + dz));
                break;
            case LINE:
                int count = Math.max(sx, Math.max(sy, sz));
                for (int i = 0; i < count; i++) {
                    int dx = sx > 1 ? i : 0;
                    int dy = sy > 1 ? i : 0;
                    int dz = sz > 1 ? i : 0;
                    blocks.add(new BlockGhostPos(cx + dx, cy + dy, cz + dz));
                }
                break;
            case SQUARE:
                for (int dx = 0; dx < sx; dx++)
                    for (int dz = 0; dz < sz; dz++) blocks.add(new BlockGhostPos(cx - halfX + dx, cy, cz - halfZ + dz));
                break;
            case WALL:
                for (int dx = 0; dx < sx; dx++) for (int dy = 0; dy < sy; dy++) {
                    blocks.add(new BlockGhostPos(cx - halfX + dx, cy + dy, cz));
                    blocks.add(new BlockGhostPos(cx - halfX + dx, cy + dy, cz + sz - 1));
                }
                for (int dz = 1; dz < sz - 1; dz++) for (int dy = 0; dy < sy; dy++) {
                    blocks.add(new BlockGhostPos(cx - halfX, cy + dy, cz + dz));
                    blocks.add(new BlockGhostPos(cx - halfX + sx - 1, cy + dy, cz + dz));
                }
                break;
            case CIRCLE:
                double radius = Math.max(sx, sz) / 2.0;
                for (int dx = -halfX; dx <= halfX; dx++)
                    for (int dz = -halfZ; dz <= halfZ; dz++) if (dx * dx + dz * dz <= radius * radius + 0.5)
                        for (int dy = 0; dy < sy; dy++) blocks.add(new BlockGhostPos(cx + dx, cy + dy, cz + dz));
                break;
            case BOX:
                for (int dx = 0; dx < sx; dx++) for (int dy = 0; dy < sy; dy++) for (int dz = 0; dz < sz; dz++)
                    if (dx == 0 || dx == sx - 1 || dy == 0 || dy == sy - 1 || dz == 0 || dz == sz - 1)
                        blocks.add(new BlockGhostPos(cx - halfX + dx, cy + dy, cz - halfZ + dz));
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
            case "PLANE":
                return BuildShape.SQUARE;
            case "WALL":
                return BuildShape.WALL;
            case "CIRCLE":
                return BuildShape.CIRCLE;
            case "BOX":
            case "CUBE":
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
