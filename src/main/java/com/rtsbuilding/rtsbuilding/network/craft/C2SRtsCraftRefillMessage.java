package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsCraftRefillMessage implements IMessage {

    private static final int BLUEPRINT_SIZE = 9;

    private List<String> blueprintItemIds;
    private String craftedItemId;
    private int craftedCount;

    public C2SRtsCraftRefillMessage() {}

    public C2SRtsCraftRefillMessage(List<String> blueprintItemIds, String craftedItemId, int craftedCount) {
        this.blueprintItemIds = blueprintItemIds;
        this.craftedItemId = craftedItemId;
        this.craftedCount = craftedCount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
            String value = blueprintItemIds != null && i < blueprintItemIds.size() ? blueprintItemIds.get(i) : "";
            value = value == null ? "" : value;
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
        String cid = craftedItemId == null ? "" : craftedItemId;
        byte[] cidBytes = cid.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(cidBytes.length);
        buf.writeBytes(cidBytes);
        buf.writeInt(Math.max(0, craftedCount));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        blueprintItemIds = new ArrayList<>(BLUEPRINT_SIZE);
        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
            int len = buf.readInt();
            if (len > 0) {
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                blueprintItemIds.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                blueprintItemIds.add("");
            }
        }
        int cidLen = buf.readInt();
        if (cidLen > 0) {
            byte[] cidBytes = new byte[cidLen];
            buf.readBytes(cidBytes);
            craftedItemId = new String(cidBytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            craftedItemId = "";
        }
        craftedCount = buf.readInt();
    }

    public List<String> getBlueprintItemIds() {
        return blueprintItemIds;
    }

    public String getCraftedItemId() {
        return craftedItemId;
    }

    public int getCraftedCount() {
        return craftedCount;
    }

    public static class Handler implements IMessageHandler<C2SRtsCraftRefillMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsCraftRefillMessage message, MessageContext ctx) {
            return null;
        }
    }
}
