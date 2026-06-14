package com.rtsbuilding.rtsbuilding.client.panel.storage;

import java.awt.Rectangle;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

import cpw.mods.fml.common.registry.GameData;

/**
 * 最近使用网格 — 横向排列最近 16 个使用的方块。
 * Bug4修复：坐标从 RtsBottomPanel 动态获取，排列在存储网格右侧。
 */
public class RecentGridView implements IRtsPanel {

    private static final String PANEL_NAME = "recent_grid";
    private static final int SLOT_SIZE = 18;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();
    private int recentX, recentY, recentW;

    public RecentGridView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(recentX, recentY, recentW, SLOT_SIZE);
    }

    @Override
    public boolean isVisible() {
        InteractionViewModel ivm = state.interaction;
        return !ivm.recentBlocks.isEmpty();
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;

        int count = Math.min(ivm.recentBlocks.size(), InteractionViewModel.MAX_RECENT_BLOCKS);
        if (count == 0) return;

        // Bug4修复: 从 RtsBottomPanel 获取坐标
        RtsBottomPanel bp = findBottomPanel(screen);
        if (bp != null) {
            recentX = bp.getRecentX();
            recentY = bp.getRecentY();
            recentW = bp.getRecentW();
        } else {
            recentX = 92;
            recentY = screen.height - 240 + 4;
            recentW = count * SLOT_SIZE;
        }

        // 标签
        String label = "Recent";
        int labelW = Math.min(recentW, count * SLOT_SIZE);
        fr.drawString(label, recentX, recentY - 10, 0xFFAAAAAA);

        for (int i = 0; i < count; i++) {
            int sx = recentX + i * SLOT_SIZE;
            int sy = recentY;

            // 背景
            Gui.drawRect(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x88444444);

            // 物品图标
            ItemStack stack = resolveStack(ivm.recentBlocks.get(i));
            if (stack != null) {
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_BLEND);
                RenderHelper.enableGUIStandardItemLighting();
                renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, stack, sx + 1, sy + 1);
                renderItem.renderItemOverlayIntoGUI(fr, mc.renderEngine, stack, sx + 1, sy + 1);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glPopMatrix();
            }
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;

        InteractionViewModel ivm = state.interaction;
        int col = (mouseX - recentX) / SLOT_SIZE;
        if (col >= 0 && col < ivm.recentBlocks.size() && mouseY >= recentY && mouseY <= recentY + SLOT_SIZE) {
            String blockId = ivm.recentBlocks.get(col);
            state.interaction.selectedBlockId = blockId;
            state.interaction.selectedBlockMeta = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        return false;
    }

    @Override
    public void resetFrameState() {}

    private ItemStack resolveStack(String blockId) {
        if (blockId == null) return null;
        try {
            ResourceLocation rl = new ResourceLocation(blockId);
            Item item = (Item) GameData.getItemRegistry()
                .getObject(rl);
            if (item != null) return new ItemStack(item);
            Block block = (Block) GameData.getBlockRegistry()
                .getObject(rl);
            if (block != null) return new ItemStack(block);
        } catch (Exception ignored) {}
        return null;
    }

    private static RtsBottomPanel findBottomPanel(GuiScreen screen) {
        if (screen instanceof RtsScreen) {
            IRtsPanel panel = ((RtsScreen) screen).getPanel(RtsBottomPanel.PANEL_NAME);
            if (panel instanceof RtsBottomPanel) {
                return (RtsBottomPanel) panel;
            }
        }
        return null;
    }
}
