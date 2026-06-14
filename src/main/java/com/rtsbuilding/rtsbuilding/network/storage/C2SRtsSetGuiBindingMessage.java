package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetGuiBindingMessage implements IMessage {

    private int slot;
    private String itemId;

    public C2SRtsSetGuiBindingMessage() {}

    public C2SRtsSetGuiBindingMessage(int s, String id) {
        slot = s;
        itemId = id != null ? id : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        writeUtf(buf, itemId, 128);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        itemId = readUtf(buf, 128);
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

    public static class Handler implements IMessageHandler<C2SRtsSetGuiBindingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetGuiBindingMessage m, MessageContext c) {
            return null;
        }
    }
}
