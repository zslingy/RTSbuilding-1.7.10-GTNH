package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel;
import com.rtsbuilding.rtsbuilding.client.CraftViewModel.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.CraftViewModel.IngredientSlot;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C 可合成列表响应消息。
 * 阶段5实现：Handler 接收并填充 CraftViewModel。
 */
public class S2CRtsCraftablesMessage implements IMessage {

    private String search;
    private boolean showUnavailable;
    private int offset;
    private boolean append;
    private boolean hasMore;
    private List<String> recipeIds;
    private List<String> resultItemIds;
    private List<Integer> resultCounts;
    private List<Boolean> craftable;
    private List<String> missingSummaries;
    private List<Integer> recipeOptionCounts;
    private List<String> optionRecipeIds;
    private List<Integer> optionResultCounts;
    private List<Boolean> optionCraftable;
    private List<String> optionSummaries;
    private List<String> optionMissingSummaries;

    public S2CRtsCraftablesMessage() {
        recipeIds = new ArrayList<>();
        resultItemIds = new ArrayList<>();
        resultCounts = new ArrayList<>();
        craftable = new ArrayList<>();
        missingSummaries = new ArrayList<>();
        recipeOptionCounts = new ArrayList<>();
        optionRecipeIds = new ArrayList<>();
        optionResultCounts = new ArrayList<>();
        optionCraftable = new ArrayList<>();
        optionSummaries = new ArrayList<>();
        optionMissingSummaries = new ArrayList<>();
    }

    public S2CRtsCraftablesMessage(String search, boolean showUnavailable, int offset, boolean append, boolean hasMore,
        List<String> recipeIds, List<String> resultItemIds, List<Integer> resultCounts, List<Boolean> craftable,
        List<String> missingSummaries, List<Integer> recipeOptionCounts, List<String> optionRecipeIds,
        List<Integer> optionResultCounts, List<Boolean> optionCraftable, List<String> optionSummaries,
        List<String> optionMissingSummaries) {
        this.search = search;
        this.showUnavailable = showUnavailable;
        this.offset = offset;
        this.append = append;
        this.hasMore = hasMore;
        this.recipeIds = recipeIds != null ? recipeIds : new ArrayList<>();
        this.resultItemIds = resultItemIds != null ? resultItemIds : new ArrayList<>();
        this.resultCounts = resultCounts != null ? resultCounts : new ArrayList<>();
        this.craftable = craftable != null ? craftable : new ArrayList<>();
        this.missingSummaries = missingSummaries != null ? missingSummaries : new ArrayList<>();
        this.recipeOptionCounts = recipeOptionCounts != null ? recipeOptionCounts : new ArrayList<>();
        this.optionRecipeIds = optionRecipeIds != null ? optionRecipeIds : new ArrayList<>();
        this.optionResultCounts = optionResultCounts != null ? optionResultCounts : new ArrayList<>();
        this.optionCraftable = optionCraftable != null ? optionCraftable : new ArrayList<>();
        this.optionSummaries = optionSummaries != null ? optionSummaries : new ArrayList<>();
        this.optionMissingSummaries = optionMissingSummaries != null ? optionMissingSummaries : new ArrayList<>();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(search != null ? search.length() : 0);
        if (search != null) buf.writeBytes(search.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        buf.writeBoolean(showUnavailable);
        buf.writeInt(offset);
        buf.writeBoolean(append);
        buf.writeBoolean(hasMore);
        int size = Math.min(
            recipeIds.size(),
            Math.min(
                resultItemIds.size(),
                Math.min(resultCounts.size(), Math.min(craftable.size(), missingSummaries.size()))));
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            writeUtf(buf, recipeIds.get(i), 256);
            writeUtf(buf, resultItemIds.get(i), 128);
            buf.writeInt(resultCounts.get(i));
            buf.writeBoolean(craftable.get(i));
            writeUtf(buf, missingSummaries.get(i), 512);
            int optCount = i < recipeOptionCounts.size() ? Math.max(0, recipeOptionCounts.get(i)) : 0;
            buf.writeInt(optCount);
        }
        int flatSize = Math.min(
            optionRecipeIds.size(),
            Math.min(
                optionResultCounts.size(),
                Math.min(optionCraftable.size(), Math.min(optionSummaries.size(), optionMissingSummaries.size()))));
        buf.writeInt(flatSize);
        for (int i = 0; i < flatSize; i++) {
            writeUtf(buf, optionRecipeIds.get(i), 256);
            buf.writeInt(optionResultCounts.get(i));
            buf.writeBoolean(optionCraftable.get(i));
            writeUtf(buf, optionSummaries.get(i), 512);
            writeUtf(buf, optionMissingSummaries.get(i), 512);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = Math.min(buf.readInt(), 128);
        byte[] b = new byte[len];
        if (len > 0) buf.readBytes(b);
        search = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        showUnavailable = buf.readBoolean();
        offset = buf.readInt();
        append = buf.readBoolean();
        hasMore = buf.readBoolean();
        int size = Math.max(0, Math.min(buf.readInt(), 65536));
        recipeIds = new ArrayList<>(size);
        resultItemIds = new ArrayList<>(size);
        resultCounts = new ArrayList<>(size);
        craftable = new ArrayList<>(size);
        missingSummaries = new ArrayList<>(size);
        recipeOptionCounts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            recipeIds.add(readUtf(buf, 256));
            resultItemIds.add(readUtf(buf, 128));
            resultCounts.add(buf.readInt());
            craftable.add(buf.readBoolean());
            missingSummaries.add(readUtf(buf, 512));
            recipeOptionCounts.add(buf.readInt());
        }
        int flatSize = Math.max(0, Math.min(buf.readInt(), 65536));
        optionRecipeIds = new ArrayList<>(flatSize);
        optionResultCounts = new ArrayList<>(flatSize);
        optionCraftable = new ArrayList<>(flatSize);
        optionSummaries = new ArrayList<>(flatSize);
        optionMissingSummaries = new ArrayList<>(flatSize);
        for (int i = 0; i < flatSize; i++) {
            optionRecipeIds.add(readUtf(buf, 256));
            optionResultCounts.add(buf.readInt());
            optionCraftable.add(buf.readBoolean());
            optionSummaries.add(readUtf(buf, 512));
            optionMissingSummaries.add(readUtf(buf, 512));
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

    // Getters
    public String getSearch() {
        return search;
    }

    public boolean isShowUnavailable() {
        return showUnavailable;
    }

    public int getOffset() {
        return offset;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public List<String> getRecipeIds() {
        return recipeIds;
    }

    public List<String> getResultItemIds() {
        return resultItemIds;
    }

    public List<Integer> getResultCounts() {
        return resultCounts;
    }

    public List<Boolean> getCraftable() {
        return craftable;
    }

    public List<String> getMissingSummaries() {
        return missingSummaries;
    }

    public static class Handler implements IMessageHandler<S2CRtsCraftablesMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsCraftablesMessage msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;

            RtsClientState state = RtsClientState.get();
            CraftViewModel cvm = state.craft;

            cvm.craftableEntries.clear();
            int count = Math.min(
                msg.getRecipeIds()
                    .size(),
                Math.min(
                    msg.getResultItemIds()
                        .size(),
                    msg.getResultCounts()
                        .size()));
            count = Math.min(
                count,
                Math.min(
                    msg.getCraftable()
                        .size(),
                    msg.getMissingSummaries()
                        .size()));

            for (int i = 0; i < count; i++) {
                String itemId = msg.getResultItemIds()
                    .get(i);
                int craftableCount = msg.getResultCounts()
                    .get(i);
                String displayName = itemId;
                boolean isCraftable = msg.getCraftable()
                    .get(i);

                CraftableEntry entry = new CraftableEntry(itemId, 0, displayName, craftableCount);
                // 从缺失摘要中解析材料需求（简化：阶段5联调用占位符）
                String missing = msg.getMissingSummaries()
                    .get(i);
                if (!missing.isEmpty()) {
                    // 有材料不足的情况，添加一个占位IngredientSlot
                    entry.ingredients.add(new IngredientSlot("minecraft:stone", 0, 1, 0));
                } else {
                    entry.ingredients.add(new IngredientSlot("minecraft:stone", 0, 1, 999));
                }
                cvm.craftableEntries.add(entry);
            }

            cvm.recipesDirty = false;
            return null;
        }
    }
}
