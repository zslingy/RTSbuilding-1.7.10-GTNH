package com.rtsbuilding.rtsbuilding.client.render;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 边界线渲染器 — 红色半透明线框显示 RTS 相机操作范围。
 * 阶段5：当 CameraViewModel 边界为空时，自动从 Config.maxActionRadiusBlocks 计算默认边界。
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
}
