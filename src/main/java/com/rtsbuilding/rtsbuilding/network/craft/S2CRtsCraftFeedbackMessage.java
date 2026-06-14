package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C 合成反馈消息。
 * 阶段5实现：Handler 接收并在 CraftViewModel 中显示反馈。
 */
public class S2CRtsCraftFeedbackMessage implements IMessage {

    private String itemId;
    private int craftedCount;
    private List<String> consumedItemIds;
    private List<Integer> consumedCounts;

    public S2CRtsCraftFeedbackMessage() {
        consumedItemIds = new ArrayList<>();
        consumedCounts = new ArrayList<>();
    }

    public S2CRtsCraftFeedbackMessage(String itemId, int craftedCount, List<String> consumedItemIds,
        List<Integer> consumedCounts) {
        this.itemId = itemId != null ? itemId : "";
        this.craftedCount = Math.max(0, craftedCount);
        this.consumedItemIds = consumedItemIds != null ? consumedItemIds : new ArrayList<>();
        this.consumedCounts = consumedCounts != null ? consumedCounts : new ArrayList<>();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf(buf, itemId, 128);
        buf.writeInt(craftedCount);
        int size = Math.min(consumedItemIds.size(), consumedCounts.size());
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            writeUtf(buf, consumedItemIds.get(i), 128);
            buf.writeInt(Math.max(0, consumedCounts.get(i)));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        itemId = readUtf(buf, 128);
        craftedCount = buf.readInt();
        int size = Math.max(0, Math.min(buf.readInt(), 65536));
        consumedItemIds = new ArrayList<>(size);
        consumedCounts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            consumedItemIds.add(readUtf(buf, 128));
            consumedCounts.add(buf.readInt());
        }
    }

    private static void writeUtf(ByteBuf buf, String s, int maxBytes) {
        if (s == null) s = "";
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = Math.min(b.length, maxBytes);
        buf.writeInt(len);
        buf.writeBytes(b, 0, len);
    }

    private static String readUtf(ByteBuf buf, int maxBytes) {
        int len = Math.max(0, Math.min(buf.readInt(), maxBytes));
        byte[] b = new byte[len];
        if (len > 0) buf.readBytes(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getItemId() {
        return itemId;
    }

    public int getCraftedCount() {
        return craftedCount;
    }

    public List<String> getConsumedItemIds() {
        return consumedItemIds;
    }

    public List<Integer> getConsumedCounts() {
        return consumedCounts;
    }

    public static class Handler implements IMessageHandler<S2CRtsCraftFeedbackMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsCraftFeedbackMessage msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;

            RtsClientState state = RtsClientState.get();
            CraftViewModel cvm = state.craft;

            if (msg.getCraftedCount() > 0) {
                cvm.showFeedback("Crafted " + msg.getCraftedCount() + "x " + msg.getItemId(), true);
                // 标记需要刷新存储列表（因为材料消耗了）
                state.storage.dirty = true;
                cvm.recipesDirty = true;
            } else {
                cvm.showFeedback("Failed to craft " + msg.getItemId() + " — insufficient materials", false);
            }

            // 关闭数量对话框
            cvm.quantityDialogOpen = false;

            return null;
        }
    }
}
