package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 开始家园选择消息。
 * 阶段C实现：Handler 触发服务端家园选择流程。
 */
public class C2SRtsBeginHomeSelectionMessage implements IMessage {

    public C2SRtsBeginHomeSelectionMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsBeginHomeSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsBeginHomeSelectionMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            RtsCameraManager.startHomeSelectionFromPanel(player);
            return null;
        }
    }
}
