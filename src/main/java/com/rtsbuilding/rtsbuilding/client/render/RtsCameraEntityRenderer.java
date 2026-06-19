package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;

public class RtsCameraEntityRenderer extends Render {

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (!(entity instanceof RtsCameraEntity)) return;
        RtsCameraEntity cam = (RtsCameraEntity) entity;

        if (!RtsClientState.get().camera.isActive) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float size = 0.3f;
        float alpha = 0.6f;
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);

        tess.setColorRGBA_F(1.0f, 1.0f, 0.8f, alpha);

        tess.addVertex(-size, 0, 0);
        tess.addVertex(size, 0, 0);
        tess.addVertex(0, -size, 0);
        tess.addVertex(0, size, 0);
        tess.addVertex(0, 0, -size);
        tess.addVertex(0, 0, size);

        tess.draw();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
