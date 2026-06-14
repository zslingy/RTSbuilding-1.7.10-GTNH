package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetQuickSlotMessage implements IMessage {

    private int slot, itemIndex;

    public C2SRtsSetQuickSlotMessage() {}

    public C2SRtsSetQuickSlotMessage(int s, int i) {
        slot = s;
        itemIndex = i;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(itemIndex);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        itemIndex = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetQuickSlotMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetQuickSlotMessage m, MessageContext c) {
            return null;
        }
    }
}
