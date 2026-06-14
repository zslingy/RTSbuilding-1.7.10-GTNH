package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsReturnCarriedMessage implements IMessage {

    private ItemStack stack;
    private int slot;

    public C2SRtsReturnCarriedMessage() {}

    public C2SRtsReturnCarriedMessage(ItemStack s, int sl) {
        stack = s != null ? s.copy() : null;
        slot = sl;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        boolean h = stack != null;
        buf.writeBoolean(h);
        if (h) ByteBufUtils.writeItemStack(buf, stack);
        buf.writeInt(slot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stack = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        slot = buf.readInt();
    }

    public ItemStack getStack() {
        return stack;
    }

    public int getSlot() {
        return slot;
    }

    public static class Handler implements IMessageHandler<C2SRtsReturnCarriedMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsReturnCarriedMessage m, MessageContext c) {
            return null;
        }
    }
}
