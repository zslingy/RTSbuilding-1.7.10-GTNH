package com.rtsbuilding.rtsbuilding.network.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import com.rtsbuilding.rtsbuilding.ClientProxy;
import com.rtsbuilding.rtsbuilding.client.CameraViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsCameraStateMessage implements IMessage {

    private boolean enabled;
    private int cameraEntityId;
    private double anchorX, anchorY, anchorZ;
    private double maxRadius, heightOffset;
    private float yawDeg, pitchDeg;
    private boolean homeSelection, closeRangeAllowed;

    public S2CRtsCameraStateMessage() {}

    public S2CRtsCameraStateMessage(boolean enabled, int cameraEntityId, double anchorX, double anchorY, double anchorZ,
        double maxRadius, double heightOffset, float yawDeg, float pitchDeg, boolean homeSelection,
        boolean closeRangeAllowed) {
        this.enabled = enabled;
        this.cameraEntityId = cameraEntityId;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.maxRadius = maxRadius;
        this.heightOffset = heightOffset;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.homeSelection = homeSelection;
        this.closeRangeAllowed = closeRangeAllowed;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(cameraEntityId);
        buf.writeDouble(anchorX);
        buf.writeDouble(anchorY);
        buf.writeDouble(anchorZ);
        buf.writeDouble(maxRadius);
        buf.writeDouble(heightOffset);
        buf.writeFloat(yawDeg);
        buf.writeFloat(pitchDeg);
        buf.writeBoolean(homeSelection);
        buf.writeBoolean(closeRangeAllowed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        enabled = buf.readBoolean();
        cameraEntityId = buf.readInt();
        anchorX = buf.readDouble();
        anchorY = buf.readDouble();
        anchorZ = buf.readDouble();
        maxRadius = buf.readDouble();
        heightOffset = buf.readDouble();
        yawDeg = buf.readFloat();
        pitchDeg = buf.readFloat();
        homeSelection = buf.readBoolean();
        closeRangeAllowed = buf.readBoolean();
    }

    // ---- Getters (unchanged) ----
    public boolean isEnabled() {
        return enabled;
    }

    public int getCameraEntityId() {
        return cameraEntityId;
    }

    public double getAnchorX() {
        return anchorX;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public double getAnchorZ() {
        return anchorZ;
    }

    public double getMaxRadius() {
        return maxRadius;
    }

    public double getHeightOffset() {
        return heightOffset;
    }

    public float getYawDeg() {
        return yawDeg;
    }

    public float getPitchDeg() {
        return pitchDeg;
    }

    public boolean isHomeSelection() {
        return homeSelection;
    }

    public boolean isCloseRangeAllowed() {
        return closeRangeAllowed;
    }

    // ---- Handler（阶段A收尾：添加视角保存/恢复） ----
    public static class Handler implements IMessageHandler<S2CRtsCameraStateMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsCameraStateMessage msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return null;

            RtsClientState state = RtsClientState.get();
            CameraViewModel camera = state.camera;

            if (msg.isEnabled()) {
                // ---- 保存原版视角状态（仅首次进入 RTS 时） ----
                camera.saveVanillaCameraState(mc);

                // 启用相机：查找服务端创建的 RtsCameraEntity 并切换渲染视角
                Entity cameraEntity = mc.theWorld.getEntityByID(msg.getCameraEntityId());
                if (cameraEntity instanceof RtsCameraEntity) {
                    RtsCameraEntity rtsCamera = (RtsCameraEntity) cameraEntity;
                    mc.renderViewEntity = rtsCamera;
                    // Bug2修复：存储相机实体引用到 ViewModel 供本地预测使用
                    camera.cameraEntity = rtsCamera;
                }
                // 更新 ViewModel
                camera.isActive = true;
                camera.posX = msg.getAnchorX();
                camera.posY = msg.getAnchorY();
                camera.posZ = msg.getAnchorZ();
                camera.rotationYaw = msg.getYawDeg();
                camera.rotationPitch = msg.getPitchDeg();
                camera.anchorX = msg.getAnchorX();
                camera.anchorY = msg.getAnchorY();
                camera.anchorZ = msg.getAnchorZ();
                camera.maxRadius = msg.getMaxRadius();
                camera.heightOffset = msg.getHeightOffset();
                camera.homeSelection = msg.isHomeSelection();
                camera.closeRangeAllowed = msg.isCloseRangeAllowed();
                // Bug2修复：设置世界边界，供 RtsWorldRenderer.hasBounds() 和子渲染器使用
                int mr = (int) Math.ceil(msg.getMaxRadius());
                camera.boundsMin = new com.rtsbuilding.rtsbuilding.util.BlockPos(
                    (int) msg.getAnchorX() - mr,
                    (int) msg.getAnchorY() - 35,
                    (int) msg.getAnchorZ() - mr);
                camera.boundsMax = new com.rtsbuilding.rtsbuilding.util.BlockPos(
                    (int) msg.getAnchorX() + mr,
                    (int) msg.getAnchorY() + 110,
                    (int) msg.getAnchorZ() + mr);
                // 启用 RTS 世界渲染器
                ClientProxy.worldRenderer.setActive(true);
            } else {
                // 禁用相机：恢复玩家视角
                if (mc.thePlayer != null) {
                    mc.renderViewEntity = mc.thePlayer;
                }
                camera.isActive = false;
                ClientProxy.worldRenderer.setActive(false);

                // ---- 恢复原版视角状态 ----
                camera.restoreVanillaCameraState(mc);
                // Bug2修复：清除相机实体引用
                camera.cameraEntity = null;
            }
            return null;
        }
    }
}
