package com.rtsbuilding.rtsbuilding.client.panel.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

public class GuidePanel {

    private static final int BOX_W = 300;
    private static final int BOX_H = 180;
    private boolean visible;
    private int currentStep;

    public void show() {
        visible = true;
        currentStep = RtsClientState.get().guideStep;
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void draw(Minecraft mc, int screenW, int screenH) {
        if (!visible || currentStep < 0 || currentStep >= GuideTypes.values().length) return;

        GuideTypes step = GuideTypes.values()[currentStep];
        FontRenderer fr = mc.fontRenderer;

        int x = (screenW - BOX_W) / 2;
        int y = (screenH - BOX_H) / 2 - 30;

        Gui.drawRect(x - 2, y - 2, x + BOX_W + 2, y + BOX_H + 2, 0xFF444444);
        Gui.drawRect(x, y, x + BOX_W, y + BOX_H, 0xCC000000);

        fr.drawString("Guide: " + step.title, x + 12, y + 10, 0xFFCC44);
        fr.drawString("Step " + (currentStep + 1) + "/" + GuideTypes.values().length, x + BOX_W - 60, y + 10, 0x888888);

        String[] lines = wrapText(mc, step.description, BOX_W - 24);
        for (int i = 0; i < lines.length; i++) {
            fr.drawString(lines[i], x + 12, y + 30 + i * (fr.FONT_HEIGHT + 2), 0xCCCCCC);
        }

        int btnY = y + BOX_H - 26;
        int prevX = x + 12;
        if (currentStep > 0) {
            Gui.drawRect(prevX, btnY, prevX + 60, btnY + 16, 0xFF444444);
            fr.drawString("< Prev", prevX + 4, btnY + 3, 0xCCCCCC);
        }

        int nextX = x + BOX_W - 72;
        String nextLabel = currentStep == GuideTypes.values().length - 1 ? "Done" : "Next >";
        Gui.drawRect(nextX, btnY, nextX + 60, btnY + 16, 0xFF446644);
        fr.drawString(nextLabel, nextX + 8, btnY + 3, 0xCCFFCC);
    }

    public void mouseClicked(int mouseX, int mouseY, int screenW, int screenH) {
        if (!visible) return;

        int x = (screenW - BOX_W) / 2;
        int y = (screenH - BOX_H) / 2 - 30;
        int btnY = y + BOX_H - 26;
        int prevX = x + 12;
        int nextX = x + BOX_W - 72;

        if (currentStep > 0 && mouseX >= prevX && mouseX <= prevX + 60 && mouseY >= btnY && mouseY <= btnY + 16) {
            currentStep--;
            RtsClientState.get().guideStep = currentStep;
            return;
        }

        if (mouseX >= nextX && mouseX <= nextX + 60 && mouseY >= btnY && mouseY <= btnY + 16) {
            if (currentStep == GuideTypes.values().length - 1) {
                visible = false;
                RtsClientState.get().guideStep = -1;
            } else {
                currentStep++;
                RtsClientState.get().guideStep = currentStep;
            }
        }
    }

    private static String[] wrapText(Minecraft mc, String text, int maxWidth) {
        return mc.fontRenderer.listFormattedStringToWidth(text, maxWidth)
            .toArray(new String[0]);
    }
}
