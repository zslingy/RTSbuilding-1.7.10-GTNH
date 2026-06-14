package com.rtsbuilding.rtsbuilding.client.overlay;

import java.awt.Rectangle;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 伤害闪烁叠加层 — 受伤时红色半透明闪烁。
 */
public class DamageFlashOverlay implements IRtsPanel {

    private static final String PANEL_NAME = "damage_flash";
    private static final int FLASH_MS = 300;

    private final RtsClientState state;
    private long flashStartMs = 0;
    private int ovW, ovH;

    public DamageFlashOverlay() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(0, 0, ovW, ovH);
    }

    @Override
    public boolean isVisible() {
        if (flashStartMs == 0) return false;
        long elapsed = System.currentTimeMillis() - flashStartMs;
        return elapsed < FLASH_MS;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        ovW = screen.width;
        ovH = screen.height;

        long elapsed = System.currentTimeMillis() - flashStartMs;
        float alpha = 0.35f * (1.0f - (float) elapsed / FLASH_MS);
        if (alpha <= 0) {
            flashStartMs = 0;
            return;
        }

        int color = ((int) (alpha * 255) << 24) | 0x00CC0000;
        Gui.drawRect(0, 0, ovW, ovH, color);
    }

    /** 触发伤害闪烁 */
    public void trigger() {
        flashStartMs = System.currentTimeMillis();
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
