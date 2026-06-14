package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsOpenGuiBindingMessage implements IMessage {

    public C2SRtsOpenGuiBindingMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsOpenGuiBindingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsOpenGuiBindingMessage m, MessageContext c) {
            return null;
        }
    }
}
