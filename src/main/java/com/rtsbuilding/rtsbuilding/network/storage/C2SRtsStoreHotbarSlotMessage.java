package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsStoreHotbarSlotMessage implements IMessage {

    private int slot, amount;

    public C2SRtsStoreHotbarSlotMessage() {}

    public C2SRtsStoreHotbarSlotMessage(int s, int a) {
        slot = s;
        amount = a;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(amount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        amount = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    public int getAmount() {
        return amount;
    }

    public static class Handler implements IMessageHandler<C2SRtsStoreHotbarSlotMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsStoreHotbarSlotMessage m, MessageContext c) {
            return null;
        }
    }
}
