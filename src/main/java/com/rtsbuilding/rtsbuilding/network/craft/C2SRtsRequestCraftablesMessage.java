package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RecipeScanCache;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 请求可合成列表消息。
 * 阶段5实现：Handler 查询服务端合成数据并回复 S2CRtsCraftablesMessage。
 * 阶段6实现：使用真实 Forge CraftingManager 配方替换模拟数据。
 */
public class C2SRtsRequestCraftablesMessage implements IMessage {

    private String search;
    private boolean showUnavailable;
    private int offset;
    private int limit;
    private boolean pinyinSearchEnabled;
    private List<String> localizedSearchMatches;

    public C2SRtsRequestCraftablesMessage() {}

    public C2SRtsRequestCraftablesMessage(String search, boolean showUnavailable, int offset, int limit,
        boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        this.search = search;
        this.showUnavailable = showUnavailable;
        this.offset = offset;
        this.limit = limit;
        this.pinyinSearchEnabled = pinyinSearchEnabled;
        this.localizedSearchMatches = localizedSearchMatches;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        String s = search == null ? "" : search;
        byte[] searchBytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(searchBytes.length);
        buf.writeBytes(searchBytes);
        buf.writeBoolean(showUnavailable);
        buf.writeInt(Math.max(0, offset));
        buf.writeInt(Math.max(1, limit));
        buf.writeBoolean(pinyinSearchEnabled);
        writeStringList(buf, localizedSearchMatches);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int searchLen = buf.readInt();
        if (searchLen > 0) {
            byte[] searchBytes = new byte[searchLen];
            buf.readBytes(searchBytes);
            search = new String(searchBytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            search = "";
        }
        showUnavailable = buf.readBoolean();
        offset = buf.readInt();
        limit = buf.readInt();
        pinyinSearchEnabled = buf.readBoolean();
        localizedSearchMatches = readStringList(buf);
    }

    private static void writeStringList(ByteBuf buf, List<String> values) {
        int size = values == null ? 0 : Math.min(values.size(), 8192);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            String v = values.get(i) == null ? "" : values.get(i);
            byte[] bytes = v.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    private static List<String> readStringList(ByteBuf buf) {
        int size = Math.min(Math.max(0, buf.readInt()), 8192);
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int len = buf.readInt();
            if (len > 0) {
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                values.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                values.add("");
            }
        }
        return values;
    }

    public String getSearch() {
        return search;
    }

    public boolean isShowUnavailable() {
        return showUnavailable;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isPinyinSearchEnabled() {
        return pinyinSearchEnabled;
    }

    public List<String> getLocalizedSearchMatches() {
        return localizedSearchMatches;
    }

    public static class Handler implements IMessageHandler<C2SRtsRequestCraftablesMessage, IMessage> {

        private static final int MAX_RECIPES_PER_REQUEST = 200;

        @Override
        public IMessage onMessage(C2SRtsRequestCraftablesMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);
            // 阶段6：AE2 优先，降级到模拟数据
            if (!RtsStorageManager.tryPopulateFromAe2(player, session)) {
                session.populateDebugData();
            }

            // 阶段6：从 Forge CraftingManager 获取真实配方
            String searchQuery = msg.getSearch();
            boolean allowUnavailable = msg.isShowUnavailable();
            int reqOffset = msg.getOffset();
            int reqLimit = Math.min(msg.getLimit(), 100);

            List<String> recipeIds = new ArrayList<>();
            List<String> resultItemIds = new ArrayList<>();
            List<Integer> resultCounts = new ArrayList<>();
            List<Boolean> craftable = new ArrayList<>();
            List<String> missingSummaries = new ArrayList<>();
            List<Integer> recipeOptionCounts = new ArrayList<>();

            try {
                List<RecipeScanCache.CachedRecipe> allRecipes = RecipeScanCache.getOrRebuild();

                // 收集所有匹配的配方条目
                List<RecipeEntry> matched = new ArrayList<>();
                int recipeIndex = 0;
                for (RecipeScanCache.CachedRecipe cr : allRecipes) {
                    if (cr == null) continue;
                    String itemId = cr.itemId;
                    if (itemId == null || itemId.isEmpty()) continue;
                    String displayName = cr.displayName;

                    // 搜索过滤
                    if (searchQuery != null && !searchQuery.isEmpty()) {
                        if (!itemId.toLowerCase(Locale.ROOT)
                            .contains(searchQuery.toLowerCase(Locale.ROOT))
                            && !displayName.toLowerCase(Locale.ROOT)
                                .contains(searchQuery.toLowerCase(Locale.ROOT))) {
                            continue;
                        }
                    }

                    // 检查材料可用性
                    List<String> inputIds = cr.inputIds;
                    List<ItemStack> inputStacks = cr.inputStacks;

                    boolean canCraft = true;
                    StringBuilder missing = new StringBuilder();
                    for (int i = 0; i < inputStacks.size(); i++) {
                        ItemStack input = inputStacks.get(i);
                        String inputId = i < inputIds.size() ? inputIds.get(i) : null;
                        if (inputId == null) continue;

                        long available = session.getCount(inputId);
                        long required = input.stackSize;

                        if (available < required) {
                            if (allowUnavailable) {
                                canCraft = false;
                            }
                            if (missing.length() > 0) missing.append(", ");
                            missing.append(inputId)
                                .append(" (need ")
                                .append(required)
                                .append(", have ")
                                .append(available)
                                .append(")");
                        }
                    }

                    // 不可用且不允许显示不可用时跳过
                    if (!canCraft && !allowUnavailable) continue;

                    matched.add(
                        new RecipeEntry("rts:recipe_" + recipeIndex, itemId, cr.count, canCraft, missing.toString()));

                    recipeIndex++;
                    if (matched.size() >= MAX_RECIPES_PER_REQUEST) break;
                }

                // 分页
                int startIdx = Math.min(reqOffset, matched.size());
                int endIdx = Math.min(startIdx + reqLimit, matched.size());

                for (int i = startIdx; i < endIdx; i++) {
                    RecipeEntry entry = matched.get(i);
                    recipeIds.add(entry.recipeId);
                    resultItemIds.add(entry.itemId);
                    resultCounts.add(entry.count);
                    craftable.add(entry.canCraft);
                    missingSummaries.add(entry.missingSummary);
                    recipeOptionCounts.add(1);
                }

            } catch (Exception e) {
                RtsbuildingMod.LOGGER.warn("C2SRtsRequestCraftablesMessage: recipe scan failed: {}", e.toString());
                // 降级：返回模拟配方
                addFallbackRecipes(
                    recipeIds,
                    resultItemIds,
                    resultCounts,
                    craftable,
                    missingSummaries,
                    recipeOptionCounts,
                    session,
                    searchQuery,
                    allowUnavailable,
                    reqOffset,
                    reqLimit);
            }

            // 空的辅助列表
            List<String> optionRecipeIds = new ArrayList<>();
            List<Integer> optionResultCounts = new ArrayList<>();
            List<Boolean> optionCraftable = new ArrayList<>();
            List<String> optionSummaries = new ArrayList<>();
            List<String> optionMissingSummaries = new ArrayList<>();

            S2CRtsCraftablesMessage response = new S2CRtsCraftablesMessage(
                msg.getSearch(),
                msg.isShowUnavailable(),
                msg.getOffset(),
                false,
                false,
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                optionRecipeIds,
                optionResultCounts,
                optionCraftable,
                optionSummaries,
                optionMissingSummaries);
            RtsNetworkManager.NETWORK.sendTo(response, player);

            return null;
        }

        /** 降级：模拟配方（当 CraftingManager 扫描失败时使用） */
        private static void addFallbackRecipes(List<String> recipeIds, List<String> resultItemIds,
            List<Integer> resultCounts, List<Boolean> craftable, List<String> missingSummaries,
            List<Integer> recipeOptionCounts, RtsStorageSession session, String search, boolean allowUnavailable,
            int offset, int limit) {

            // 仅返回少量基础配方作为降级
            addEntry(
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                "rts:oak_planks",
                "minecraft:oak_planks",
                4,
                session,
                new String[] { "minecraft:oak_log" },
                new int[] { 1 });
            addEntry(
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                "rts:sticks",
                "minecraft:stick",
                4,
                session,
                new String[] { "minecraft:oak_planks" },
                new int[] { 2 });
            addEntry(
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                "rts:crafting_table",
                "minecraft:crafting_table",
                1,
                session,
                new String[] { "minecraft:oak_planks" },
                new int[] { 4 });
            addEntry(
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                "rts:furnace",
                "minecraft:furnace",
                1,
                session,
                new String[] { "minecraft:cobblestone" },
                new int[] { 8 });
        }

        private static void addEntry(List<String> recipeIds, List<String> resultItemIds, List<Integer> resultCounts,
            List<Boolean> craftable, List<String> missingSummaries, List<Integer> recipeOptionCounts, String recipeId,
            String resultItemId, int resultCount, RtsStorageSession session, String[] requiredItems,
            int[] requiredCounts) {

            recipeIds.add(recipeId);
            resultItemIds.add(resultItemId);
            resultCounts.add(resultCount);
            recipeOptionCounts.add(1);

            boolean canCraft = true;
            StringBuilder missing = new StringBuilder();
            for (int i = 0; i < requiredItems.length; i++) {
                long available = session.getCount(requiredItems[i]);
                if (available < requiredCounts[i]) {
                    canCraft = false;
                    if (missing.length() > 0) missing.append(", ");
                    missing.append(requiredItems[i])
                        .append(" (need ")
                        .append(requiredCounts[i])
                        .append(", have ")
                        .append(available)
                        .append(")");
                }
            }
            craftable.add(canCraft);
            missingSummaries.add(missing.toString());
        }

        /** 配方条目（中间数据结构） */
        private static class RecipeEntry {

            final String recipeId;
            final String itemId;
            final int count;
            final boolean canCraft;
            final String missingSummary;

            RecipeEntry(String recipeId, String itemId, int count, boolean canCraft, String missingSummary) {
                this.recipeId = recipeId;
                this.itemId = itemId;
                this.count = count;
                this.canCraft = canCraft;
                this.missingSummary = missingSummary;
            }
        }
    }
}
