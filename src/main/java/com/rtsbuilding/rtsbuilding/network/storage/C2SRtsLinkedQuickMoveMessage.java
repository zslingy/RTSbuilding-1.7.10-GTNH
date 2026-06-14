package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsLinkedQuickMoveMessage implements IMessage {

    private int slot, mode;

    public C2SRtsLinkedQuickMoveMessage() {}

    public C2SRtsLinkedQuickMoveMessage(int s, int m) {
        slot = s;
        mode = m;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        mode = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    public int getMode() {
        return mode;
    }

    public static class Handler implements IMessageHandler<C2SRtsLinkedQuickMoveMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsLinkedQuickMoveMessage m, MessageContext c) {
            return null;
        }
    }
}
