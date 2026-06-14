package com.rtsbuilding.rtsbuilding.client.panel.fluid;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

/**
 * 流体面板视图 — 2列流体网格，显示存储中的流体。
 * 坐标从 RtsBottomPanel 动态获取，位于存储网格左侧。
 */
public class FluidStripView implements IRtsPanel {

    private static final String PANEL_NAME = "fluid_strip";
    private static final int SLOT_SIZE = 18;
    private static final int FLUID_COLS = 2;

    private final RtsClientState state;
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

        // 桩：无实际流体数据时显示空条
        fr.drawString("(no\nfluid\n data)", stripX + 2, stripY + stripH / 2 - 12, 0xFF666666);
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
