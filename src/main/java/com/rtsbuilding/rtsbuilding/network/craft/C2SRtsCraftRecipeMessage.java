package com.rtsbuilding.rtsbuilding.network.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 合成请求消息。
 * 阶段5实现：Handler 检查材料并执行合成。
 */
public class C2SRtsCraftRecipeMessage implements IMessage {

    private String recipeId;
    private int craftCount;

    public C2SRtsCraftRecipeMessage() {}

    public C2SRtsCraftRecipeMessage(String recipeId, int craftCount) {
        this.recipeId = recipeId;
        this.craftCount = craftCount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        String id = recipeId == null ? "" : recipeId;
        byte[] bytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        buf.writeInt(Math.max(1, craftCount));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        if (len > 0) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            recipeId = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            recipeId = "";
        }
        craftCount = Math.max(1, buf.readInt());
    }

    public String getRecipeId() {
        return recipeId;
    }

    public int getCraftCount() {
        return craftCount;
    }

    public static class Handler implements IMessageHandler<C2SRtsCraftRecipeMessage, IMessage> {

        /** 模拟配方表（阶段5联调用） */
        private static final String[][] RECIPES = { { "rts:oak_planks", "minecraft:oak_planks", "minecraft:oak_log" },
            { "rts:sticks", "minecraft:stick", "minecraft:oak_planks" },
            { "rts:crafting_table", "minecraft:crafting_table", "minecraft:oak_planks" },
            { "rts:furnace", "minecraft:furnace", "minecraft:cobblestone" },
            { "rts:chest", "minecraft:chest", "minecraft:oak_planks" },
            { "rts:torch", "minecraft:torch", "minecraft:coal" },
            { "rts:iron_pickaxe", "minecraft:iron_pickaxe", "minecraft:iron_ingot" },
            { "rts:glass", "minecraft:glass", "minecraft:sand" }, };

        @Override
        public IMessage onMessage(C2SRtsCraftRecipeMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);

            // 查找配方
            String[] recipe = null;
            for (String[] r : RECIPES) {
                if (r[0].equals(msg.getRecipeId())) {
                    recipe = r;
                    break;
                }
            }

            List<String> consumedIds = new ArrayList<>();
            List<Integer> consumedCounts = new ArrayList<>();
            boolean success = false;
            String resultItemId = "unknown";
            int craftedCount = 0;

            if (recipe != null) {
                resultItemId = recipe[1];
                long available = session.getCount(recipe[2]);
                int needed = msg.getCraftCount();
                if (available >= needed) {
                    // 消耗材料
                    session.addItem(recipe[2], 0, -needed);
                    consumedIds.add(recipe[2]);
                    consumedCounts.add(needed);
                    // 产出成品（简化：直接加到存储）
                    session.addItem(resultItemId, 0, msg.getCraftCount());
                    craftedCount = msg.getCraftCount();
                    success = true;
                }
            }

            S2CRtsCraftFeedbackMessage feedback = new S2CRtsCraftFeedbackMessage(
                resultItemId,
                craftedCount,
                consumedIds,
                consumedCounts);
            RtsNetworkManager.NETWORK.sendTo(feedback, player);

            return null;
        }
    }
}
