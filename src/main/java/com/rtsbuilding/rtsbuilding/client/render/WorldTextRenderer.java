package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

public class WorldTextRenderer {

    public static void drawLabel(String text, double x, double y, double z, int color) {
        if (text == null || text.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        if (fr == null) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(-mc.renderViewEntity.rotationYaw, 0f, 1f, 0f);
        GL11.glRotatef(mc.renderViewEntity.rotationPitch, 1f, 0f, 0f);
        GL11.glScalef(-0.025f, -0.025f, 0.025f);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int w = fr.getStringWidth(text);
        fr.drawString(text, -w / 2, 0, color);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    public static void drawBoxLabel(String text, double minX, double minY, double minZ, double maxX, double maxY,
        double maxZ) {
        if (text == null || text.isEmpty()) return;
        double cx = (minX + maxX) / 2.0;
        double cy = maxY + 0.5;
        double cz = (minZ + maxZ) / 2.0;
        drawLabel(text, cx, cy, cz, 0xCCFFFF);
    }
}
