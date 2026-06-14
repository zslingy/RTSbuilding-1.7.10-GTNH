package com.rtsbuilding.rtsbuilding.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsInteractionHandler;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 交互目标高亮渲染器 — 鼠标悬停目标方块的黄色线框高亮。
 * 原依赖 objectMouseOver，但 RTS 模式下 inGameHasFocus=false 导致其始终为 null。
 * 改为使用 RtsInteractionHandler.computeCursorRay() 基于鼠标坐标独立计算射线，
 * 对齐原版 ScreenCursorPicker 的行为。
 */
public class InteractionTargetRenderer {

    public void render(RtsClientState state) {
        if (!state.camera.isActive) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        // Bug2修复：使用独立射线检测，不依赖 mc.objectMouseOver
        // LWJGL 2 原生鼠标坐标（display 空间），computeCursorRay 内部会归一化
        int rawMouseX = org.lwjgl.input.Mouse.getX();
        int rawMouseY = org.lwjgl.input.Mouse.getY();
        // 转换为屏幕 Y 轴朝下的坐标
        int screenY = mc.displayHeight - rawMouseY - 1;

        // 优先使用 localMirror（客户端即时创建），回退到 cameraEntity
        RtsCameraEntity camera = state.camera.localMirror;
        if (camera == null) camera = state.camera.cameraEntity;
        if (camera == null) return;
        double eyeY = camera.posY + camera.getEyeHeight();

        World world = camera.worldObj;
        if (world == null) return;

        // 独立计算光标射线并进行射线检测
        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);
        Vec3 look = RtsInteractionHandler.computeCursorRay(camera, rawMouseX, screenY, mc);
        Vec3 end = start.addVector(look.xCoord * 200.0D, look.yCoord * 200.0D, look.zCoord * 200.0D);

        MovingObjectPosition mop = world.rayTraceBlocks(start, end);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        BlockPos target = new BlockPos(mop.blockX, mop.blockY, mop.blockZ);

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glPolygonOffset(-1.0f, -1.0f);
        GL11.glColor4f(1.0f, 1.0f, 0.3f, 0.6f);
        GL11.glLineWidth(2.5f);

        // 方块六个面的线框（完整立方体线框，不只是顶面）
        double x1 = target.getX();
        double y1 = target.getY();
        double z1 = target.getZ();
        double x2 = x1 + 1;
        double y2 = y1 + 1;
        double z2 = z1 + 1;

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

        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
