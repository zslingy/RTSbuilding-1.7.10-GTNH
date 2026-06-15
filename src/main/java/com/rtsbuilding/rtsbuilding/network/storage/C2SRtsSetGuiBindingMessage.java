package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetGuiBindingMessage implements IMessage {

    private int slot;
    private String itemId;
    private boolean clear;
    private int posX, posY, posZ;
    private byte face;
    private boolean hasPosition;

    public C2SRtsSetGuiBindingMessage() {}

    public C2SRtsSetGuiBindingMessage(int s, String id) {
        this(s, id, false, 0, 0, 0, (byte) 0);
    }

    public C2SRtsSetGuiBindingMessage(int slot, String itemId, boolean clear, int posX, int posY, int posZ, byte face) {
        this.slot = slot;
        this.itemId = itemId != null ? itemId : "";
        this.clear = clear;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.face = face;
        this.hasPosition = posX != 0 || posY != 0 || posZ != 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        writeUtf(buf, itemId, 128);
        buf.writeBoolean(clear);
        buf.writeBoolean(hasPosition);
        if (hasPosition) {
            buf.writeInt(posX);
            buf.writeInt(posY);
            buf.writeInt(posZ);
            buf.writeByte(face);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        itemId = readUtf(buf, 128);
        clear = buf.readBoolean();
        hasPosition = buf.readBoolean();
        if (hasPosition) {
            posX = buf.readInt();
            posY = buf.readInt();
            posZ = buf.readInt();
            face = buf.readByte();
        }
    }

    private static void writeUtf(ByteBuf b, String s, int max) {
        if (s == null) s = "";
        byte[] d = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int l = Math.min(d.length, max);
        b.writeInt(l);
        b.writeBytes(d, 0, l);
    }

    private static String readUtf(ByteBuf b, int max) {
        int l = Math.max(0, Math.min(b.readInt(), max));
        byte[] d = new byte[l];
        if (l > 0) b.readBytes(d);
        return new String(d, java.nio.charset.StandardCharsets.UTF_8);
    }

    public int getSlot() {
        return slot;
    }

    public String getItemId() {
        return itemId;
    }

    public boolean isClear() {
        return clear;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public byte getFace() {
        return face;
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetGuiBindingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetGuiBindingMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageManager.setGuiBinding(
                player,
                (byte) m.getSlot(),
                m.isClear(),
                m.getPosX(),
                m.getPosY(),
                m.getPosZ(),
                m.getFace(),
                m.getItemId());
            return null;
        }
    }
}
