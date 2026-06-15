package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsInteractionHandler;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 交互目标高亮渲染器 — 鼠标悬停目标方块/实体的黄色线框高亮。
 * Issue 7: 射线命中实体时显示实体的模型框，不显示方块预览框。
 */
public class InteractionTargetRenderer {

    public void render(RtsClientState state) {
        if (!state.camera.isActive) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        int rawMouseX = org.lwjgl.input.Mouse.getX();
        int rawMouseY = org.lwjgl.input.Mouse.getY();
        int screenY = mc.displayHeight - rawMouseY - 1;

        RtsCameraEntity camera = state.camera.localMirror;
        if (camera == null) camera = state.camera.cameraEntity;
        if (camera == null) return;
        double eyeY = camera.posY + camera.getEyeHeight();

        World world = camera.worldObj;
        if (world == null) return;

        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);
        Vec3 look = RtsInteractionHandler.computeCursorRay(camera, rawMouseX, screenY, mc);
        Vec3 end = start.addVector(look.xCoord * 200.0D, look.yCoord * 200.0D, look.zCoord * 200.0D);

        // 方块射线检测
        MovingObjectPosition blockHit = world.rayTraceBlocks(start, end);
        double blockDist = blockHit != null ? start.distanceTo(blockHit.hitVec) : 200.0D;

        // 实体射线检测
        Entity closestEntity = null;
        double closestEntityDist = Double.MAX_VALUE;
        for (Object obj : world.loadedEntityList) {
            if (!(obj instanceof Entity)) continue;
            Entity entity = (Entity) obj;
            if (!entity.isEntityAlive() || entity == mc.thePlayer) continue;

            float expand = entity.getCollisionBorderSize();
            AxisAlignedBB aabb = entity.boundingBox.expand(expand, expand, expand);
            MovingObjectPosition entityHit = aabb.calculateIntercept(start, end);
            if (entityHit != null) {
                double dist = start.distanceTo(entityHit.hitVec);
                if (dist < closestEntityDist) {
                    closestEntityDist = dist;
                    closestEntity = entity;
                }
            }
        }

        // 判断实体是否比方块更近
        boolean hitEntity = closestEntity != null && closestEntityDist < blockDist;

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(-1.0f, -1.0f);
        GL11.glLineWidth(2.5f);

        if (hitEntity) {
            // Issue 7: 渲染实体碰撞箱
            AxisAlignedBB bb = closestEntity.boundingBox;
            // 渲染时需要减去实体的renderPos（RtsWorldRenderer已经做了相机偏移）
            double x1 = bb.minX;
            double y1 = bb.minY;
            double z1 = bb.minZ;
            double x2 = bb.maxX;
            double y2 = bb.maxY;
            double z2 = bb.maxZ;

            // 黄色实体框
            GL11.glColor4f(1.0f, 1.0f, 0.3f, 0.8f);
            drawBoxLines(x1, y1, z1, x2, y2, z2);
        } else if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            // 渲染方块线框
            BlockPos target = new BlockPos(blockHit.blockX, blockHit.blockY, blockHit.blockZ);
            GL11.glColor4f(1.0f, 1.0f, 0.3f, 0.6f);
            double x1 = target.getX();
            double y1 = target.getY();
            double z1 = target.getZ();
            drawBoxLines(x1, y1, z1, x1 + 1, y1 + 1, z1 + 1);
        }

        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void drawBoxLines(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        // Bottom face
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y1, z1);
        // Top face
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z2);
        GL11.glVertex3d(x1, y2, z1);
        // Vertical edges
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y2, z1);
        GL11.glVertex3d(x2, y1, z1);
        GL11.glVertex3d(x2, y2, z1);
        GL11.glVertex3d(x2, y1, z2);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x1, y1, z2);
        GL11.glVertex3d(x1, y2, z2);
        GL11.glEnd();
    }
}
