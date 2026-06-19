package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * 1.7.10 固定管线下的窗口按钮纹理绘制辅助。
 */
public final class WindowButton {

    private WindowButton() {}

    public static void drawTexture(Minecraft mc, ResourceLocation texture, int x, int y, int width, int height) {
        if (mc == null || texture == null || width <= 0 || height <= 0) return;

        mc.getTextureManager()
            .bindTexture(texture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + height, 0, 0, 1);
        tess.addVertexWithUV(x + width, y + height, 0, 1, 1);
        tess.addVertexWithUV(x + width, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }
}
