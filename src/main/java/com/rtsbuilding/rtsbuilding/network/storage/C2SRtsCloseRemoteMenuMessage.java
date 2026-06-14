package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsCloseRemoteMenuMessage implements IMessage {

    public C2SRtsCloseRemoteMenuMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsCloseRemoteMenuMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsCloseRemoteMenuMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player != null) {
                player.closeContainer();
            }
            return null;
        }
    }
}
