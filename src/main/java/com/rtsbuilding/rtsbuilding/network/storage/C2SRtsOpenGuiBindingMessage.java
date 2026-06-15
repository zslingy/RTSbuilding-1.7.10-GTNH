package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsOpenGuiBindingMessage implements IMessage {

    private byte slot;

    public C2SRtsOpenGuiBindingMessage() {}

    public C2SRtsOpenGuiBindingMessage(byte slot) {
        this.slot = slot;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(slot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readByte();
    }

    public byte getSlot() {
        return slot;
    }

    public static class Handler implements IMessageHandler<C2SRtsOpenGuiBindingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsOpenGuiBindingMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageManager.openGuiBinding(player, m.getSlot());
            return null;
        }
    }
}
