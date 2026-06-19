package com.rtsbuilding.rtsbuilding.client.render;

import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;

public class BlueprintGhostRenderer {

    public static void render(RtsBlueprint blueprint, float partialTicks) {
        if (blueprint == null) return;

        List<RtsBlueprintBlock> blocks = blueprint.getBlocks();
        if (blocks == null || blocks.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0.3f, 0.5f, 1.0f, 0.35f);

        Tessellator tess = Tessellator.instance;
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) continue;
            int x = block.getRelativePos()
                .getX();
            int y = block.getRelativePos()
                .getY();
            int z = block.getRelativePos()
                .getZ();
            renderGhostBlock(tess, x, y, z);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void renderGhostBlock(Tessellator tess, int x, int y, int z) {
        double x0 = x;
        double y0 = y;
        double z0 = z;
        double x1 = x + 1;
        double y1 = y + 1;
        double z1 = z + 1;

        tess.startDrawingQuads();

        tess.addVertex(x0, y0, z0);
        tess.addVertex(x1, y0, z0);
        tess.addVertex(x1, y0, z1);
        tess.addVertex(x0, y0, z1);

        tess.addVertex(x0, y1, z0);
        tess.addVertex(x0, y1, z1);
        tess.addVertex(x1, y1, z1);
        tess.addVertex(x1, y1, z0);

        tess.addVertex(x0, y0, z0);
        tess.addVertex(x0, y1, z0);
        tess.addVertex(x1, y1, z0);
        tess.addVertex(x1, y0, z0);

        tess.addVertex(x0, y0, z1);
        tess.addVertex(x1, y0, z1);
        tess.addVertex(x1, y1, z1);
        tess.addVertex(x0, y1, z1);

        tess.addVertex(x0, y0, z0);
        tess.addVertex(x0, y0, z1);
        tess.addVertex(x0, y1, z1);
        tess.addVertex(x0, y1, z0);

        tess.addVertex(x1, y0, z0);
        tess.addVertex(x1, y1, z0);
        tess.addVertex(x1, y1, z1);
        tess.addVertex(x1, y0, z1);

        tess.draw();
    }
}
