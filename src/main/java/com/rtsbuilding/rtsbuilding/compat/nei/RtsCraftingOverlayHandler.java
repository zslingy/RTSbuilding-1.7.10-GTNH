package com.rtsbuilding.rtsbuilding.compat.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipeMessage;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;

/**
 * RTS Crafting Overlay Handler — 处理 NEI → RTS 合成终端的配方转移。
 *
 * 当玩家在 NEI 配方界面点击"+"按钮时，此 handler 将配方
 * 通过 C2SRtsCraftRecipeMessage 发送到 RTS 服务端执行合成。
 *
 * 采用 PositionedStack 正确提取 NEI 配方数据（1.7.10 NEI 2.x API）。
 */
public class RtsCraftingOverlayHandler implements IOverlayHandler {

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        if (recipe == null) return;

        try {
            // NEI getResultStack 返回 PositionedStack，.item 获取 ItemStack
            PositionedStack resultPos = recipe.getResultStack(recipeIndex);
            if (resultPos == null) return;
            ItemStack output = resultPos.item;
            if (output == null || output.getItem() == null) return;

            String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(output.getItem());
            if (itemId == null || itemId.isEmpty()) return;

            int count = output.stackSize;
            int requestCount = maxTransfer ? Math.min(count * 64, 64) : count;

            // 构造 recipeId（格式：rts:<item路径>）
            String recipeId = "rts:" + itemId.replace("minecraft:", "");

            // 发送合成请求到服务端
            RtsNetworkManager.NETWORK.sendToServer(new C2SRtsCraftRecipeMessage(recipeId, requestCount));

            // 显示反馈
            RtsClientState state = RtsClientState.get();
            if (state != null) {
                state.craft.showFeedback("Requested: " + output.getDisplayName() + " x" + requestCount, true);
            }

        } catch (Exception e) {
            // 静默降级
        }
    }

    @Override
    public List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        List<ItemOverlayState> itemPresenceSlots = new ArrayList<>();
        if (recipe == null || firstGui == null) return itemPresenceSlots;

        try {
            List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
            if (ingredients == null) return itemPresenceSlots;

            // 收集容器中所有可用物品
            final List<ItemStack> invStacks = new ArrayList<>();
            for (Object slotObj : firstGui.inventorySlots.inventorySlots) {
                net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) slotObj;
                if (s != null && s.getStack() != null
                    && s.getStack().stackSize > 0
                    && s.canTakeStack(firstGui.mc.thePlayer)) {
                    invStacks.add(
                        s.getStack()
                            .copy());
                }
            }

            for (PositionedStack stack : ingredients) {
                Optional<ItemStack> used = invStacks.stream()
                    .filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findAny();

                itemPresenceSlots.add(new ItemOverlayState(stack, used.isPresent()));

                if (used.isPresent()) {
                    used.get().stackSize -= 1;
                }
            }
        } catch (Exception e) {
            // 静默降级
        }

        return itemPresenceSlots;
    }
}
