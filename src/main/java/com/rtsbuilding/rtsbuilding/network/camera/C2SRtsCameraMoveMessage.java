package com.rtsbuilding.rtsbuilding.network.camera;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 相机移动消息 — 客户端每 tick 发送完整 10 字段相机输入到服务端。
 *
 * 阶段A：Handler 完整实现，调用 RtsCameraManager.move()。
 */
public class C2SRtsCameraMoveMessage implements IMessage {

    private float forward, strafe, vertical;
    private float panX, panY, rotateX, rotateY, scroll;
    private int rotateSteps;
    private boolean fast;

    public C2SRtsCameraMoveMessage() {}

    public C2SRtsCameraMoveMessage(float forward, float strafe, float vertical, float panX, float panY, float rotateX,
        float rotateY, float scroll, int rotateSteps, boolean fast) {
        this.forward = forward;
        this.strafe = strafe;
        this.vertical = vertical;
        this.panX = panX;
        this.panY = panY;
        this.rotateX = rotateX;
        this.rotateY = rotateY;
        this.scroll = scroll;
        this.rotateSteps = rotateSteps;
        this.fast = fast;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
        buf.writeFloat(vertical);
        buf.writeFloat(panX);
        buf.writeFloat(panY);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(scroll);
        buf.writeInt(rotateSteps);
        buf.writeBoolean(fast);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        forward = buf.readFloat();
        strafe = buf.readFloat();
        vertical = buf.readFloat();
        panX = buf.readFloat();
        panY = buf.readFloat();
        rotateX = buf.readFloat();
        rotateY = buf.readFloat();
        scroll = buf.readFloat();
        rotateSteps = buf.readInt();
        fast = buf.readBoolean();
    }

    public float getForward() {
        return forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public float getVertical() {
        return vertical;
    }

    public float getPanX() {
        return panX;
    }

    public float getPanY() {
        return panY;
    }

    public float getRotateX() {
        return rotateX;
    }

    public float getRotateY() {
        return rotateY;
    }

    public float getScroll() {
        return scroll;
    }

    public int getRotateSteps() {
        return rotateSteps;
    }

    public boolean isFast() {
        return fast;
    }

    /**
     * 服务端 Handler — 将 10 字段相机输入委托给 RtsCameraManager.move()。
     */
    public static class Handler implements IMessageHandler<C2SRtsCameraMoveMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsCameraMoveMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsCameraManager.move(
                player,
                msg.getForward(),
                msg.getStrafe(),
                msg.getVertical(),
                msg.getPanX(),
                msg.getPanY(),
                msg.getRotateX(),
                msg.getRotateY(),
                msg.getScroll(),
                msg.getRotateSteps(),
                msg.isFast());
            return null;
        }
    }
}
