package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public final class WindowSlider {

    public final int x, y, trackWidth;
    public final float min, max;
    public float value;
    public boolean dragging;
    private boolean enabled = true;
    private int knobRadius = 5;

    public WindowSlider(int x, int y, int trackWidth, float min, float max, float startValue) {
        this.x = x;
        this.y = y;
        this.trackWidth = Math.max(16, trackWidth);
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(max, startValue));
    }

    public void draw(Minecraft mc, int mouseX, int mouseY) {
        int trackY = y;
        int trackH = 4;
        int knobX = x + (int) ((value - min) / (max - min) * trackWidth);

        Gui.drawRect(x, trackY, x + trackWidth, trackY + trackH, 0xFF333333);
        Gui.drawRect(x, trackY, x + trackWidth, trackY + 1, 0xFF555555);

        int knobColor = dragging ? 0xFFAAAAFF : 0xFF8888FF;
        int kr = knobRadius;
        Gui.drawRect(knobX - kr, y - kr, knobX + kr, y + kr, knobColor);
    }

    public void mouseClicked(int mouseX, int mouseY) {
        if (!enabled) return;
        if (mouseX >= x - knobRadius && mouseX <= x + trackWidth + knobRadius
            && mouseY >= y - knobRadius
            && mouseY <= y + knobRadius) {
            dragging = true;
            updateValue(mouseX);
        }
    }

    public void mouseReleased() {
        dragging = false;
    }

    public void mouseDragged(int mouseX) {
        if (dragging) updateValue(mouseX);
    }

    public void mouseWheel(int delta) {
        if (!enabled) return;
        float step = (max - min) / Math.max(1, trackWidth / 4);
        value = Math.max(min, Math.min(max, value + (delta > 0 ? step : -step)));
    }

    private void updateValue(int mouseX) {
        float t = (float) (mouseX - x) / trackWidth;
        value = min + t * (max - min);
        value = Math.max(min, Math.min(max, value));
    }

    public void setEnabled(boolean e) {
        enabled = e;
    }

    public int getValueI() {
        return Math.round(value);
    }
}
