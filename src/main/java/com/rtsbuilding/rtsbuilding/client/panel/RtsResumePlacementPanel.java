package com.rtsbuilding.rtsbuilding.client.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionMessage;

public class RtsResumePlacementPanel {

    private static final int BUTTON_W = 140;
    private static final int BUTTON_H = 20;

    private int x, y;
    private boolean visible;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void draw(Minecraft mc) {
        int count = RtsClientState.get().interaction.pendingPlacementCount;
        if (count <= 0) {
            visible = false;
            return;
        }
        visible = true;
        FontRenderer fr = mc.fontRenderer;

        Gui.drawRect(x, y, x + BUTTON_W + 16, y + BUTTON_H + 8, 0xCC222222);
        Gui.drawRect(x, y, x + BUTTON_W + 16, y + 1, 0xFF555555);

        String text = "Resume " + count + " pending placements";
        fr.drawString(text, x + 8, y + 4, 0xFFCC44);

        int btnX = x + 8;
        int btnY = y + BUTTON_H + 12;
        Gui.drawRect(btnX, btnY, btnX + BUTTON_W, btnY + BUTTON_H, 0xFF3344AA);
        fr.drawString("Execute All", btnX + 6, btnY + (BUTTON_H - fr.FONT_HEIGHT) / 2, 0xFFFFFF);

        int cancelX = btnX;
        int cancelY = btnY + BUTTON_H + 4;
        Gui.drawRect(cancelX, cancelY, cancelX + BUTTON_W, cancelY + BUTTON_H, 0xFF663333);
        fr.drawString("Discard All", cancelX + 6, cancelY + (BUTTON_H - fr.FONT_HEIGHT) / 2, 0xFFAAAA);
    }

    public void mouseClicked(int mouseX, int mouseY) {
        if (!visible) return;
        int btnX = x + 8;
        int btnY = y + BUTTON_H + 12;

        if (isInside(mouseX, mouseY, btnX, btnY, BUTTON_W, BUTTON_H)) {
            RtsNetworkManager.NETWORK.sendToServer(new C2SRtsResumePlacementActionMessage(0, true));
        } else if (isInside(mouseX, mouseY, btnX, btnY + BUTTON_H + 4, BUTTON_W, BUTTON_H)) {
            RtsClientState.get().interaction.pendingPlacementCount = 0;
        }
    }

    private static boolean isInside(int mx, int my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    public boolean isVisible() {
        return visible;
    }
}
