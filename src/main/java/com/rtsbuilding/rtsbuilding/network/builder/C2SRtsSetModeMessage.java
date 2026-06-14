package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetModeMessage implements IMessage {

    private byte mode;

    public C2SRtsSetModeMessage() {}

    public C2SRtsSetModeMessage(byte mode) {
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
    }

    public byte getMode() {
        return mode;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetModeMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetModeMessage msg, MessageContext ctx) {
            return null;
        }
    }
}
