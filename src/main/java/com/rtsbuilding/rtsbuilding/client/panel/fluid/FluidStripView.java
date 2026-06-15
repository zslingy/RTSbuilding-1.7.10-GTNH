package com.rtsbuilding.rtsbuilding.client.panel.fluid;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

import cpw.mods.fml.common.registry.GameData;

/**
 * 流体面板视图 — 2列流体网格，显示存储中的流体。
 * 坐标从 RtsBottomPanel 动态获取，位于存储网格左侧。
 */
public class FluidStripView implements IRtsPanel {

    private static final String PANEL_NAME = "fluid_strip";
    private static final int SLOT_SIZE = 9;
    private static final int FLUID_COLS = 2;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();
    private int stripX, stripY, stripW, stripH;

    public FluidStripView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(stripX, stripY, stripW, stripH);
    }

    @Override
    public boolean isVisible() {
        RtsBottomPanel bp = findBottomPanel(null);
        if (bp != null && !bp.isStorageTab()) return false;
        StorageViewModel svm = state.storage;
        if ("fluids".equals(svm.activeCategory)) return true;
        for (StorageViewModel.StorageEntry e : svm.entries) {
            if (e != null && e.itemId != null && isFluidItem(e.itemId)) return true;
        }
        return false;
    }

    private static boolean isFluidItem(String itemId) {
        return itemId != null && (itemId.contains("bucket") || itemId.contains("bottle")
            || itemId.contains("potion")
            || itemId.contains("fluid"));
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;

        // 从 RtsBottomPanel 获取坐标（流体条在存储网格左侧）
        RtsBottomPanel bp = findBottomPanel(screen);
        if (bp != null) {
            stripX = bp.getFluidStripX();
            stripY = bp.getStorageY();
            stripW = FLUID_COLS * SLOT_SIZE;
            stripH = bp.getGridH();
        } else {
            stripX = 80;
            stripY = screen.height - 110 - 4 + 18 + 4 + 14 + 4;
            stripW = FLUID_COLS * SLOT_SIZE;
            stripH = 80;
        }

        // 背景
        Gui.drawRect(stripX, stripY, stripX + stripW, stripY + stripH, 0xAA2E1E12);

        // 顶边高亮
        Gui.drawRect(stripX, stripY, stripX + stripW, stripY + 1, 0xFFFFA553);

        // 标签
        fr.drawString("Fluids", stripX, stripY - 12, 0xFFCCCC44);

        // 修复: 从存储条目中筛选流体物品并渲染
        java.util.List<StorageViewModel.StorageEntry> fluidEntries = new java.util.ArrayList<>();
        for (StorageViewModel.StorageEntry e : state.storage.entries) {
            if (e != null && e.itemId != null && isFluidItem(e.itemId)) {
                fluidEntries.add(e);
            }
        }

        if (fluidEntries.isEmpty()) {
            fr.drawString("(no fluid)", stripX + 2, stripY + stripH / 2 - 4, 0xFF666666);
            return;
        }

        // 渲染流体物品网格
        int rows = Math.min(fluidEntries.size() / FLUID_COLS + 1, stripH / SLOT_SIZE);
        for (int i = 0; i < fluidEntries.size() && i < FLUID_COLS * rows; i++) {
            int col = i % FLUID_COLS;
            int row = i / FLUID_COLS;
            int sx = stripX + col * SLOT_SIZE;
            int sy = stripY + row * SLOT_SIZE;

            // 槽位背景
            Gui.drawRect(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x88444444);

            // 物品图标
            ItemStack stack = resolveStack(fluidEntries.get(i));
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

    private ItemStack resolveStack(StorageViewModel.StorageEntry entry) {
        if (entry == null || entry.itemId == null) return null;
        try {
            net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation(entry.itemId);
            net.minecraft.item.Item item = (net.minecraft.item.Item) GameData.getItemRegistry()
                .getObject(rl);
            if (item == null) return null;
            return new ItemStack(item, 1, entry.meta);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!isVisible()) return false;
        if (mouseX >= stripX && mouseX <= stripX + stripW && mouseY >= stripY && mouseY <= stripY + stripH) {
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

    private static RtsBottomPanel findBottomPanel(GuiScreen screen) {
        if (screen instanceof RtsScreen) {
            IRtsPanel panel = ((RtsScreen) screen).getPanel(RtsBottomPanel.PANEL_NAME);
            if (panel instanceof RtsBottomPanel) return (RtsBottomPanel) panel;
        }
        return null;
    }
}
