package com.rtsbuilding.rtsbuilding.network.progression;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsQuestDetectMessage implements IMessage {

    private byte mode;
    public static final byte MODE_MANUAL = 0;

    public C2SRtsQuestDetectMessage() {}

    public C2SRtsQuestDetectMessage(byte mode) {
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

    public static class Handler implements IMessageHandler<C2SRtsQuestDetectMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsQuestDetectMessage msg, MessageContext ctx) {
            return null; // stub — stage 3
        }
    }
}
