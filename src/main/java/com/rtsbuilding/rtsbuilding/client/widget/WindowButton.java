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

    /**
     * 绘制整张纹理，UV 覆盖 0→1。
     */
    public static void drawTexture(Minecraft mc, ResourceLocation texture, int x, int y, int width, int height) {
        drawTextureRegion(mc, texture, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    /**
     * spritesheet 子区域绘制，通过像素坐标指定纹理中的矩形区域。
     *
     * @param textureU     纹理中采样子区域左上角 X（像素）
     * @param textureV     纹理中采样子区域左上角 Y（像素）
     * @param regionWidth  子区域宽（像素）
     * @param regionHeight 子区域高（像素）
     * @param texWidth     纹理文件总宽（像素）
     * @param texHeight    纹理文件总高（像素）
     */
    public static void drawTexture(Minecraft mc, ResourceLocation texture, int x, int y, int width, int height,
        int textureU, int textureV, int regionWidth, int regionHeight, int texWidth, int texHeight) {
        float u0 = (float) textureU / texWidth;
        float v0 = (float) textureV / texHeight;
        float u1 = (float) (textureU + regionWidth) / texWidth;
        float v1 = (float) (textureV + regionHeight) / texHeight;
        drawTextureRegion(mc, texture, x, y, width, height, u0, v0, u1, v1);
    }

    /**
     * 核心绘制方法：将纹理中 (u0,v0)-(u1,v1) 区域映射到屏幕 (x,y)-(x+w,y+h)。
     */
    private static void drawTextureRegion(Minecraft mc, ResourceLocation texture, int x, int y, int width, int height,
        float u0, float v0, float u1, float v1) {
        if (mc == null || texture == null || width <= 0 || height <= 0) return;

        mc.getTextureManager()
            .bindTexture(texture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + height, 0, u0, v1);
        tess.addVertexWithUV(x + width, y + height, 0, u1, v1);
        tess.addVertexWithUV(x + width, y, 0, u1, v0);
        tess.addVertexWithUV(x, y, 0, u0, v0);
        tess.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }
}
