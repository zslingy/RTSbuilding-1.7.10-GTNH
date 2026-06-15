package com.rtsbuilding.rtsbuilding.client.render;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 边界线渲染器 — 红色半透明线框显示 RTS 相机操作范围。
 * 阶段5：当 CameraViewModel 边界为空时，自动从 Config.maxActionRadiusBlocks 计算默认边界。
 * P2-5：新增区块边界渲染（3x3区块，绿色线框）。
 */
public class BoundaryRenderer {

    public void render(RtsClientState state) {
        if (!state.camera.isActive) return;

        BlockPos min, max;
        if (state.camera.hasBounds()) {
            min = state.camera.boundsMin;
            max = state.camera.boundsMax;
        } else {
            // 无显式边界时，从相机位置 + 配置的 maxActionRadius 计算默认边界
            int radius = Math.max(48, Math.min(512, Config.maxActionRadiusBlocks));
            int cx = (int) state.camera.posX;
            int cy = (int) state.camera.posY;
            int cz = (int) state.camera.posZ;
            min = new BlockPos(cx - radius, cy - 32, cz - radius);
            max = new BlockPos(cx + radius, cy + 32, cz + radius);
        }

        int x1 = min.getX(), y1 = min.getY(), z1 = min.getZ();
        int x2 = max.getX() + 1, y2 = max.getY() + 1, z2 = max.getZ() + 1;

        GL11.glColor4f(1.0f, 0.3f, 0.3f, 0.5f);
        GL11.glBegin(GL11.GL_LINES);

        // Bottom face
        drawLineLoop(x1, y1, z1, x2, z2);
        // Top face
        drawLineLoop(x1, y2, z1, x2, z2);
        // Verticals
        verticals(x1, y1, y2, z1);
        verticals(x2, y1, y2, z1);
        verticals(x1, y1, y2, z2);
        verticals(x2, y1, y2, z2);

        GL11.glEnd();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * P2-5: 渲染以相机为中心的3x3区块边界（绿色半透明线框）。
     */
    public void renderChunkBoundaries(RtsClientState state) {
        if (!state.camera.isActive) return;

        // 计算相机所在区块坐标
        int camChunkX = ((int) state.camera.posX) >> 4;
        int camChunkZ = ((int) state.camera.posZ) >> 4;

        // Y轴范围：使用相机Y±32
        int y1 = (int) state.camera.posY - 32;
        int y2 = (int) state.camera.posY + 32;

        GL11.glColor4f(0.3f, 1.0f, 0.3f, 0.4f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);

        // 3x3区块：从(camChunkX-1)到(camChunkX+2)，共4条X方向线和4条Z方向线
        for (int dx = -1; dx <= 2; dx++) {
            int x = (camChunkX + dx) << 4; // 区块起始X坐标
            // Z方向线
            int z1 = (camChunkZ - 1) << 4;
            int z2 = (camChunkZ + 2) << 4;
            // 底面
            GL11.glVertex3d(x, y1, z1);
            GL11.glVertex3d(x, y1, z2);
            // 顶面
            GL11.glVertex3d(x, y2, z1);
            GL11.glVertex3d(x, y2, z2);
            // 垂直线
            for (int dz = -1; dz <= 2; dz++) {
                int z = (camChunkZ + dz) << 4;
                GL11.glVertex3d(x, y1, z);
                GL11.glVertex3d(x, y2, z);
            }
        }
        for (int dz = -1; dz <= 2; dz++) {
            int z = (camChunkZ + dz) << 4;
            // X方向线
            int x1 = (camChunkX - 1) << 4;
            int x2 = (camChunkX + 2) << 4;
            // 底面
            GL11.glVertex3d(x1, y1, z);
            GL11.glVertex3d(x2, y1, z);
            // 顶面
            GL11.glVertex3d(x1, y2, z);
            GL11.glVertex3d(x2, y2, z);
        }

        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public void renderFunnelBounds(RtsClientState state) {
        if (!state.camera.isActive || !state.interaction.funnelActive || !state.interaction.funnelHasTarget) return;

        double size = Math.max(1, Math.min(16, state.interaction.funnelRangeSize));
        double half = size / 2.0D;
        double x1 = state.interaction.funnelTargetX - half;
        double y1 = state.interaction.funnelTargetY - half;
        double z1 = state.interaction.funnelTargetZ - half;
        double x2 = state.interaction.funnelTargetX + half;
        double y2 = state.interaction.funnelTargetY + half;
        double z2 = state.interaction.funnelTargetZ + half;

        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5f);
        GL11.glColor4f(1.0f, 0.92f, 0.05f, 0.95f);
        GL11.glBegin(GL11.GL_LINES);

        drawBoxEdges(x1, y1, z1, x2, y2, z2);

        GL11.glEnd();
        if (depthWasEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void drawLineLoop(int x1, int y, int z1, int x2, int z2) {
        GL11.glVertex3d(x1, y, z1);
        GL11.glVertex3d(x2, y, z1);
        GL11.glVertex3d(x2, y, z2);
        GL11.glVertex3d(x1, y, z2);
        GL11.glVertex3d(x1, y, z1);
    }

    private static void verticals(int x, int y1, int y2, int z) {
        GL11.glVertex3d(x, y1, z);
        GL11.glVertex3d(x, y2, z);
    }

    private static void drawBoxEdges(double x1, double y1, double z1, double x2, double y2, double z2) {
        edge(x1, y1, z1, x2, y1, z1);
        edge(x2, y1, z1, x2, y1, z2);
        edge(x2, y1, z2, x1, y1, z2);
        edge(x1, y1, z2, x1, y1, z1);

        edge(x1, y2, z1, x2, y2, z1);
        edge(x2, y2, z1, x2, y2, z2);
        edge(x2, y2, z2, x1, y2, z2);
        edge(x1, y2, z2, x1, y2, z1);

        edge(x1, y1, z1, x1, y2, z1);
        edge(x2, y1, z1, x2, y2, z1);
        edge(x2, y1, z2, x2, y2, z2);
        edge(x1, y1, z2, x1, y2, z2);
    }

    private static void edge(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }
}
