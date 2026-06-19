package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ChatAllowedCharacters;

public final class WindowTextBox {

    public final int x, y, width, height;
    private String text = "";
    private int cursorPos;
    private boolean focused;
    private boolean enabled = true;
    private int maxLength = 64;

    public WindowTextBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void draw(Minecraft mc) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, focused ? 0xFFAAAAAA : 0xFF555555);
        Gui.drawRect(x, y, x + width, y + height, 0xFF000000);

        FontRenderer fr = mc.fontRenderer;
        String display = text;
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            if (cursorPos >= text.length()) {
                display = text + "_";
            } else {
                display = text.substring(0, cursorPos) + "_" + text.substring(cursorPos);
            }
        }

        int textX = x + 3;
        int textY = y + (height - fr.FONT_HEIGHT) / 2;
        fr.drawString(display, textX, textY, 0xE0E0E0);
    }

    public void mouseClicked(int mouseX, int mouseY) {
        focused = enabled && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        if (focused) cursorPos = text.length();
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!focused || !enabled) return;

        if (keyCode == 1) {
            focused = false;
            return;
        }
        if (keyCode == 28) {
            focused = false;
            return;
        }
        if (keyCode == 14) {
            if (text.length() > 0 && cursorPos > 0) {
                text = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                cursorPos--;
            }
            return;
        }
        if (keyCode == 203) {
            if (cursorPos > 0) cursorPos--;
            return;
        }
        if (keyCode == 205) {
            if (cursorPos < text.length()) cursorPos++;
            return;
        }

        if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && text.length() < maxLength) {
            text = text.substring(0, cursorPos) + typedChar + text.substring(cursorPos);
            cursorPos++;
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String t) {
        text = t != null ? t : "";
        cursorPos = text.length();
    }

    public void setEnabled(boolean e) {
        enabled = e;
        if (!enabled) focused = false;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean f) {
        focused = f;
    }
}
