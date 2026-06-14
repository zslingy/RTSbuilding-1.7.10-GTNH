package com.rtsbuilding.rtsbuilding.client.popup;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 合成反馈弹出 — 成功/失败消息，约 2 秒后自动消失。
 * 浮动在屏幕底部中央。
 */
public class CraftFeedbackPopup implements IRtsPanel {

    private static final String PANEL_NAME = "craft_feedback";
    private static final int POPUP_W = 240;
    private static final int POPUP_H = 30;
    private static final long DISPLAY_MS = 2500;

    private final RtsClientState state;
    private int popupX, popupY;
    private long popupStartMs;

    public CraftFeedbackPopup() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(popupX, popupY, POPUP_W, POPUP_H);
    }

    @Override
    public boolean isVisible() {
        CraftViewModel cvm = state.craft;
        if (!cvm.isFeedbackActive()) return false;
        long elapsed = System.currentTimeMillis() - cvm.feedbackTimestamp;
        return elapsed < DISPLAY_MS && !cvm.feedbackMessage.isEmpty();
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        CraftViewModel cvm = state.craft;
        FontRenderer fr = screen.mc.fontRenderer;

        popupX = (screen.width - POPUP_W) / 2;
        popupY = screen.height - 40;

        // 背景（成功=绿，失败=红）
        int bgColor = cvm.feedbackSuccess ? 0xCC226622 : 0xCC662222;
        Gui.drawRect(popupX, popupY, popupX + POPUP_W, popupY + POPUP_H, bgColor);

        // 消息文字
        String text = (cvm.feedbackSuccess ? "[OK] " : "[FAIL] ") + cvm.feedbackMessage;
        int textW = fr.getStringWidth(text);
        fr.drawString(text, popupX + (POPUP_W - textW) / 2, popupY + 8, 0xFFFFFFFF);

        // 剩余时间指示条
        long elapsed = System.currentTimeMillis() - cvm.feedbackTimestamp;
        float remaining = 1.0f - (float) elapsed / DISPLAY_MS;
        if (remaining > 0) {
            int barW = (int) (POPUP_W * remaining);
            Gui.drawRect(popupX, popupY + POPUP_H - 3, popupX + barW, popupY + POPUP_H - 1, 0xFF88CC88);
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!isVisible()) return false;
        // 点击任意位置关闭
        state.craft.feedbackMessage = "";
        state.craft.feedbackTimestamp = 0;
        return true;
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
}
