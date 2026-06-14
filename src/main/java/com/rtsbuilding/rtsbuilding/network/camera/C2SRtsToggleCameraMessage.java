package com.rtsbuilding.rtsbuilding.network.camera;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsToggleCameraMessage implements IMessage {

    private boolean startAtPlayerHead;

    public C2SRtsToggleCameraMessage() {}

    public C2SRtsToggleCameraMessage(boolean startAtPlayerHead) {
        this.startAtPlayerHead = startAtPlayerHead;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(startAtPlayerHead);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        startAtPlayerHead = buf.readBoolean();
    }

    public boolean isStartAtPlayerHead() {
        return startAtPlayerHead;
    }

    public static class Handler implements IMessageHandler<C2SRtsToggleCameraMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsToggleCameraMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (RtsCameraManager.isCameraActive(player.getUniqueID())) {
                // 已激活 → 禁用相机
                RtsCameraManager.disableCamera(player);
            } else {
                // 未激活 → 启用相机
                RtsCameraManager.enableCamera(player, msg.isStartAtPlayerHead());
            }
            return null;
        }
    }
}
