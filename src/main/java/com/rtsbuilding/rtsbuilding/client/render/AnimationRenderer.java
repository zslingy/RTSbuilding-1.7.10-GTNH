package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

/**
 * Bug4修复：放置/破坏动画渲染器 — 挖掘进度覆盖层 + 放置脉冲动画。
 * 
 * 挖掘进度覆盖层：在 RtsMineManager 逐 tick 推进挖掘时，根据 S2CRtsMineProgressMessage
 * 的阶段值 (0-9)，在目标方块位置渲染半透明红色覆盖层。
 * 阶段越高，红色越深，透明度越高。
 */
public class AnimationRenderer {

    private long lastAnimMs = 0;
    private float animScale = 1.0f;

    public void render(RtsClientState state, float partialTicks) {
        // Bug4修复：渲染挖掘进度覆盖层
        renderMineProgress(state);

        // 放置脉冲动画
        long now = System.currentTimeMillis();
        if (now - lastAnimMs > 300) {
            animScale = 1.0f + 0.1f * (float) Math.sin(now / 200.0);
            lastAnimMs = now;
        }
    }

    /**
     * Bug4修复：渲染当前活跃挖掘的进度覆盖层。
     * 红色半透明覆盖，阶段越高越明显（0=几乎透明, 9=深红高透明度）。
     * stage=10 时清除状态（由 Handler 处理），此处只渲染 stage 0-9。
     */
    private void renderMineProgress(RtsClientState state) {
        if (state == null || state.interaction == null) return;
        if (state.interaction.mineProgressX < 0) return; // 无活跃挖掘

        int stage = state.interaction.mineProgressStage & 0xFF;
        if (stage < 0 || stage >= 10) return; // 只渲染进度阶段

        int x = state.interaction.mineProgressX;
        int y = state.interaction.mineProgressY;
        int z = state.interaction.mineProgressZ;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        Block block = mc.theWorld.getBlock(x, y, z);
        if (block == null || block.isAir(mc.theWorld, x, y, z)) {
            // 已被破坏 → 清除状态
            state.interaction.mineProgressX = -1;
            state.interaction.mineProgressY = -1;
            state.interaction.mineProgressZ = -1;
            state.interaction.mineProgressStage = 0;
            return;
        }

        float progress = (stage + 1) / 10.0f; // 0.1 → 1.0
        float alpha = 0.15f + progress * 0.35f; // 0.15 → 0.50
        float red = 0.9f;
        float green = 0.1f + (1.0f - progress) * 0.2f;
        float blue = 0.1f;

        GL11.glColor4f(red, green, blue, alpha);
        GL11.glLineWidth(3.0f);

        double x1 = x;
        double y1 = y;
        double z1 = z;
        double x2 = x + 1;
        double y2 = y + 1;
        double z2 = z + 1;

        // 渲染破坏阶段裂纹图案：用交叉线密度表示进度
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);

        // 正面 (Z+)
        float step = 0.25f;
        for (float u = 0; u <= 1.0f; u += step) {
            tess.addVertex(x1 + u, y1, z2);
            tess.addVertex(x1 + u, y2, z2);
            tess.addVertex(x1, y1 + u, z2);
            tess.addVertex(x2, y1 + u, z2);
        }
        // 背面 (Z-)
        for (float u = 0; u <= 1.0f; u += step) {
            tess.addVertex(x1 + u, y1, z1);
            tess.addVertex(x1 + u, y2, z1);
            tess.addVertex(x1, y1 + u, z1);
            tess.addVertex(x2, y1 + u, z1);
        }
        // 顶面
        for (float u = 0; u <= 1.0f; u += step) {
            tess.addVertex(x1 + u, y2, z1);
            tess.addVertex(x1 + u, y2, z2);
            tess.addVertex(x1, y2, z1 + u);
            tess.addVertex(x2, y2, z1 + u);
        }
        // 侧面 (X+)
        for (float u = 0; u <= 1.0f; u += step) {
            tess.addVertex(x2, y1 + u, z1);
            tess.addVertex(x2, y1 + u, z2);
            tess.addVertex(x2, y1, z1 + u);
            tess.addVertex(x2, y2, z1 + u);
        }
        // 侧面 (X-)
        for (float u = 0; u <= 1.0f; u += step) {
            tess.addVertex(x1, y1 + u, z1);
            tess.addVertex(x1, y1 + u, z2);
            tess.addVertex(x1, y1, z1 + u);
            tess.addVertex(x1, y2, z1 + u);
        }

        // 根据进度阶段添加更多裂纹（高频网格）
        if (stage >= 5) {
            float fineStep = 0.125f;
            for (float u = 0; u <= 1.0f; u += fineStep) {
                if (u % step != 0) {
                    tess.addVertex(x1 + u, y1, z2);
                    tess.addVertex(x1 + u, y2, z2);
                }
            }
        }

        tess.draw();

        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
