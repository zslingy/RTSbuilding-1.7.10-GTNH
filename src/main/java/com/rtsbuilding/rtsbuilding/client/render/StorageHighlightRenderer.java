package com.rtsbuilding.rtsbuilding.client.render;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 存储方块高亮渲染器 — 半透明绿色高亮已连接的存储方块。
 * Bug6修复：添加距离裁剪和数量上限，优化渲染性能。
 */
public class StorageHighlightRenderer {

    /** 最大渲染距离（方块距离相机的 Manhattan 距离上限） */
    private static final int MAX_RENDER_DISTANCE = 64;
    /** 最多渲染的存储方块数量，超过时跳过远端 */
    private static final int MAX_RENDER_COUNT = 48;

    public void render(RtsClientState state) {
        List<BlockPos> linkedPositions = state.storage.linkedStoragePositions;
        if (linkedPositions == null || linkedPositions.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderViewEntity == null || !state.camera.isActive) return;

        double cx = state.camera.posX;
        double cy = state.camera.posY;
        double cz = state.camera.posZ;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        long now = System.currentTimeMillis();
        float alpha = 0.25f + 0.10f * (float) Math.sin(now * 0.003);

        int rendered = 0;
        for (BlockPos pos : linkedPositions) {
            if (pos == null) continue;
            double px = pos.getX() + 0.5D;
            double py = pos.getY() + 0.5D;
            double pz = pos.getZ() + 0.5D;

            // Bug6修复：Manhattan 距离裁剪
            double dx = Math.abs(px - cx);
            double dy = Math.abs(py - cy);
            double dz = Math.abs(pz - cz);
            if (dx + dy + dz > MAX_RENDER_DISTANCE) continue;

            rendered++;
            if (rendered > MAX_RENDER_COUNT) break;

            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();

            // 填充面
            GL11.glColor4f(0.2f, 1.0f, 0.2f, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            cube(x, y, z, x + 1.0, y + 1.0, z + 1.0);
            GL11.glEnd();

            // 边框线
            GL11.glColor4f(0.2f, 1.0f, 0.2f, alpha + 0.3f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(x, y, z);
            GL11.glVertex3d(x, y, z + 1.0);
            GL11.glVertex3d(x + 1.0, y, z + 1.0);
            GL11.glVertex3d(x + 1.0, y, z);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex3d(x, y + 1.0, z);
            GL11.glVertex3d(x, y + 1.0, z + 1.0);
            GL11.glVertex3d(x + 1.0, y + 1.0, z + 1.0);
            GL11.glVertex3d(x + 1.0, y + 1.0, z);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(x, y, z);
            GL11.glVertex3d(x, y + 1.0, z);
            GL11.glVertex3d(x, y, z + 1.0);
            GL11.glVertex3d(x, y + 1.0, z + 1.0);
            GL11.glVertex3d(x + 1.0, y, z + 1.0);
            GL11.glVertex3d(x + 1.0, y + 1.0, z + 1.0);
            GL11.glVertex3d(x, y, z);
            GL11.glVertex3d(x + 1.0, y, z);
            GL11.glVertex3d(x, y + 1.0, z);
            GL11.glVertex3d(x + 1.0, y + 1.0, z);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopAttrib();
    }

    private static void cube(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Top
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y2, z2);
        // Bottom
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z1);
        // Front
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y2, z2);
        // Back
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1);
        // Left
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z1);
        // Right
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2);
    }
}
