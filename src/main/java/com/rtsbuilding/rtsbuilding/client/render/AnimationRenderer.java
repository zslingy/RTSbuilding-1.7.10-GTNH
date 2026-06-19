package com.rtsbuilding.rtsbuilding.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

public class AnimationRenderer {

    private static final List<PlaceAnimation> placeAnimations = new ArrayList<>();
    private static final List<BreakAnimation> breakAnimations = new ArrayList<>();
    private static final int DURATION = 10;
    private static final int MAX_ANIMATIONS = 64;

    public static void addPlace(int x, int y, int z) {
        if (placeAnimations.size() >= MAX_ANIMATIONS) placeAnimations.remove(0);
        placeAnimations.add(new PlaceAnimation(x, y, z));
    }

    public static void addBreak(int x, int y, int z) {
        if (breakAnimations.size() >= MAX_ANIMATIONS) breakAnimations.remove(0);
        breakAnimations.add(new BreakAnimation(x, y, z));
    }

    public static void render(float partialTicks) {
        Tessellator tess = Tessellator.instance;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2.0f);

        // 放置动画：绿色线框从 0.5 缩放到 1.0
        Iterator<PlaceAnimation> pi = placeAnimations.iterator();
        while (pi.hasNext()) {
            PlaceAnimation a = pi.next();
            a.age++;
            if (a.age > DURATION) {
                pi.remove();
                continue;
            }
            float progress = a.age / (float) DURATION;
            float scale = 0.5f + progress * 0.5f;
            float alpha = 1.0f - progress;
            float offset = (1.0f - scale) * 0.5f;

            double x0 = a.x + offset;
            double y0 = a.y + offset;
            double z0 = a.z + offset;
            double x1 = a.x + 1.0 - offset;
            double y1 = a.y + 1.0 - offset;
            double z1 = a.z + 1.0 - offset;

            GL11.glColor4f(0.2f, 1.0f, 0.2f, alpha);
            drawWireBox(tess, x0, y0, z0, x1, y1, z1);
        }

        // 破坏动画：红色线框从 1.0 缩放到 0.3
        Iterator<BreakAnimation> bi = breakAnimations.iterator();
        while (bi.hasNext()) {
            BreakAnimation a = bi.next();
            a.age++;
            if (a.age > DURATION) {
                bi.remove();
                continue;
            }
            float progress = a.age / (float) DURATION;
            float scale = 1.0f - progress * 0.7f;
            float alpha = 1.0f - progress;
            float offset = (1.0f - scale) * 0.5f;

            double x0 = a.x + offset;
            double y0 = a.y + offset;
            double z0 = a.z + offset;
            double x1 = a.x + 1.0 - offset;
            double y1 = a.y + 1.0 - offset;
            double z1 = a.z + 1.0 - offset;

            GL11.glColor4f(1.0f, 0.2f, 0.2f, alpha);
            drawWireBox(tess, x0, y0, z0, x1, y1, z1);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawWireBox(Tessellator tess, double x0, double y0, double z0, double x1, double y1,
        double z1) {
        tess.startDrawing(GL11.GL_LINES);

        tess.addVertex(x0, y0, z0);
        tess.addVertex(x1, y0, z0);
        tess.addVertex(x0, y0, z0);
        tess.addVertex(x0, y1, z0);
        tess.addVertex(x0, y0, z0);
        tess.addVertex(x0, y0, z1);

        tess.addVertex(x1, y0, z0);
        tess.addVertex(x1, y1, z0);
        tess.addVertex(x1, y0, z0);
        tess.addVertex(x1, y0, z1);

        tess.addVertex(x0, y1, z0);
        tess.addVertex(x1, y1, z0);
        tess.addVertex(x0, y1, z0);
        tess.addVertex(x0, y1, z1);

        tess.addVertex(x0, y0, z1);
        tess.addVertex(x1, y0, z1);
        tess.addVertex(x0, y0, z1);
        tess.addVertex(x0, y1, z1);

        tess.addVertex(x1, y1, z1);
        tess.addVertex(x0, y1, z1);
        tess.addVertex(x1, y1, z1);
        tess.addVertex(x1, y0, z1);
        tess.addVertex(x1, y1, z1);
        tess.addVertex(x1, y1, z0);

        tess.draw();
    }

    private static class PlaceAnimation {

        final int x, y, z;
        int age;

        PlaceAnimation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class BreakAnimation {

        final int x, y, z;
        int age;

        BreakAnimation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
