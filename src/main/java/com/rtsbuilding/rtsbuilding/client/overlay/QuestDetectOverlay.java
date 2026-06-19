package com.rtsbuilding.rtsbuilding.client.overlay;

import java.awt.Rectangle;

import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 任务检测叠加层 — 显示当前检测到的任务进度。
 */
public class QuestDetectOverlay implements IRtsPanel {

    private static final String PANEL_NAME = "quest_detect";
    private final RtsClientState state;
    private int ovX, ovY;

    public QuestDetectOverlay() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(ovX, ovY, 200, 30);
    }

    @Override
    public boolean isVisible() {
        return state.progression.questDetectPhase >= 0;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        ovX = screen.width / 2 - 100;
        ovY = 10;

        int scanned = state.progression.questDetectScanned;
        int total = state.progression.questDetectTotal;
        String text = "Quest Scan: " + scanned + " / " + total;
        int color = scanned >= total ? 0x55FF55 : 0xFFCC44;

        net.minecraft.client.gui.Gui.drawRect(ovX, ovY, ovX + 200, ovY + 30, 0xCC111111);
        net.minecraft.client.gui.Gui.drawRect(ovX, ovY, ovX + 200, ovY + 1, 0xFF666666);
        screen.mc.fontRenderer.drawString(text, ovX + 8, ovY + 11, color);
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
