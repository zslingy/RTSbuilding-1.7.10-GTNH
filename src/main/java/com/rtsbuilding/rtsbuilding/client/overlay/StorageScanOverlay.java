package com.rtsbuilding.rtsbuilding.client.overlay;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 存储扫描叠加层 — 存储扫描进度条。
 */
public class StorageScanOverlay implements IRtsPanel {

    private static final String PANEL_NAME = "storage_scan";
    private final RtsClientState state;
    private int ovX, ovY;

    public StorageScanOverlay() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(ovX, ovY, 200, 20);
    }

    @Override
    public boolean isVisible() {
        return state.storage.dirty; // 需要刷新时显示
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;
        FontRenderer fr = screen.mc.fontRenderer;
        ovX = screen.width - 210;
        ovY = 12;

        Gui.drawRect(ovX, ovY, ovX + 200, ovY + 20, 0xCC333333);
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.overlay.scanning_storage"),
            ovX + 4,
            ovY + 4,
            0xFFCCCC44);

        // 扫描进度条（简化为脉冲）
        long t = System.currentTimeMillis() / 100;
        int barW = (int) (200 * (0.3 + 0.4 * Math.sin(t * 0.1)));
        Gui.drawRect(ovX, ovY + 16, ovX + barW, ovY + 20, 0xFFCC8844);
    }

    @Override
    public boolean onMouseClick(int mx, int my, int b) {
        return false;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int s) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int k) {
        return false;
    }

    @Override
    public void resetFrameState() {}
}
