package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsStoreFluidMessage implements IMessage {

    private byte sourceType, toolSlot;
    private String itemId;
    public static final byte SOURCE_STORAGE_ITEM = 0;
    public static final byte SOURCE_TOOL_SLOT = 1;
    public static final byte SOURCE_PIN_ITEM = 2;

    public C2SRtsStoreFluidMessage() {}

    public C2SRtsStoreFluidMessage(byte sourceType, byte toolSlot, String itemId) {
        this.sourceType = sourceType;
        this.toolSlot = toolSlot;
        this.itemId = itemId != null ? itemId : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(sourceType);
        buf.writeByte(toolSlot);
        writeUtf(buf, itemId, 128);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sourceType = buf.readByte();
        toolSlot = buf.readByte();
        itemId = readUtf(buf, 128);
    }

    public byte getSourceType() {
        return sourceType;
    }

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getItemId() {
        return itemId;
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

    public static class Handler implements IMessageHandler<C2SRtsStoreFluidMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsStoreFluidMessage msg, MessageContext ctx) {
            return null;
        }
    }
}
