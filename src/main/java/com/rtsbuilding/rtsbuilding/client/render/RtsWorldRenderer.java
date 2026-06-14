package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * RTS 世界叠加层调度器 — 替代原 RtsVisualOverlayRenderer。
 * 注册到 Forge RenderWorldLastEvent，调度 5 个子渲染器。
 */
public class RtsWorldRenderer {

    private final RtsClientState state;
    private final BoundaryRenderer boundaryRenderer;
    private final StorageHighlightRenderer storageHighlightRenderer;
    private final InteractionTargetRenderer interactionTargetRenderer;
    private final ShapeGhostRenderer shapeGhostRenderer;
    private final AnimationRenderer animationRenderer;

    private boolean active = false;

    public RtsWorldRenderer() {
        this.state = RtsClientState.get();
        this.boundaryRenderer = new BoundaryRenderer();
        this.storageHighlightRenderer = new StorageHighlightRenderer();
        this.interactionTargetRenderer = new InteractionTargetRenderer();
        this.shapeGhostRenderer = new ShapeGhostRenderer();
        this.animationRenderer = new AnimationRenderer();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!active) return;
        if (!state.camera.hasBounds()) return;

        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.entity.EntityLivingBase renderView = mc.renderViewEntity;
        if (renderView == null) return;

        // 阶段7: 远距离跳过（2×maxActionRadiusBlocks 外不渲染）
        double cx = state.camera.posX;
        double cy = state.camera.posY;
        double cz = state.camera.posZ;
        final int maxRenderDist = com.rtsbuilding.rtsbuilding.Config.maxActionRadiusBlocks * 2;

        double px = renderView.lastTickPosX + (renderView.posX - renderView.lastTickPosX) * event.partialTicks;
        double py = renderView.lastTickPosY + (renderView.posY - renderView.lastTickPosY) * event.partialTicks;
        double pz = renderView.lastTickPosZ + (renderView.posZ - renderView.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);

        // 阶段7: 远距离跳过
        if (renderView.getDistance(cx, cy, cz) > maxRenderDist) {
            GL11.glPopMatrix();
            return;
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(2.0f);

        boundaryRenderer.render(state);
        storageHighlightRenderer.render(state);
        interactionTargetRenderer.render(state);
        ShapeGhostRenderer.ShapesRenderData ghost = shapeGhostRenderer.computeShapes(state);
        if (!ghost.blocks.isEmpty()) {
            shapeGhostRenderer.renderShapesAbs(ghost);
        }
        animationRenderer.render(state, event.partialTicks);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    public void cleanup() {
        active = false;
    }

    // ---- 阶段7: 视锥裁剪辅助方法 ----

    /**
     * 简易视锥检测：前方 180° + 距离上限。
     * 各子渲染器在迭代目标时可调用此方法跳过不可见方块。
     */
    public static boolean isInViewFrustum(double bx, double by, double bz, EntityPlayer player, int maxDist) {
        double dx = bx - player.posX;
        double dy = by - player.posY;
        double dz = bz - player.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > maxDist * maxDist) return false;
        float yaw = player.rotationYaw * (float) Math.PI / 180f;
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double dot = dx * fx + dz * fz;
        return dot > -maxDist * 0.5;
    }
}
