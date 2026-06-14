package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 请求进度状态消息。
 * 阶段C实现：Handler 调用 RtsProgressionManager.syncToPlayer 回复完整状态。
 */
public class C2SRtsRequestProgressionStateMessage implements IMessage {

    public C2SRtsRequestProgressionStateMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsRequestProgressionStateMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsRequestProgressionStateMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            RtsProgressionManager.syncToPlayer(player);
            return null;
        }
    }
}
