package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * 浮动窗口面板基类 — 1.7.10 适配版。
 * 实现可拖动窗口框架（标题栏、关闭按钮、背景边框），子面板负责内容渲染。
 * 替代原版 RtsWindowPanel（NeoForge GuiGraphics 体系）。
 */
public abstract class RtsWindowPanel implements IRtsPanel {

    private static final int TITLE_BAR_H = 16;
    private static final int CLOSE_BTN_W = 14;
    private static final int SCREEN_MARGIN = 4;
    private static final int MIN_W = 80;
    private static final int MIN_H = 60;

    protected int windowX, windowY;
    protected int windowWidth, windowHeight;
    protected boolean open = true;
    protected boolean mouseHovering;
    protected boolean draggable = true;

    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    @Override
    public final String panelName() {
        return getWindowId();
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(windowX, windowY, windowWidth, windowHeight);
    }

    @Override
    public boolean isVisible() {
        return open && canShowWindow();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        boolean wasOpen = this.open;
        this.open = open;
        if (open && !wasOpen) {
            ensureBounds();
        }
        if (!open && wasOpen) {
            onClose();
        }
    }

    public void toggleOpen() {
        setOpen(!this.open);
    }

    @Override
    public final void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) {
            mouseHovering = false;
            return;
        }
        ensureBounds();
        clampToScreen(screen);
        mouseHovering = isInsideWindow(mouseX, mouseY);

        renderWindowFrame(screen, mouseX, mouseY);

        int contentX = windowX + 2;
        int contentY = windowY + TITLE_BAR_H;
        int contentW = windowWidth - 4;
        int contentH = windowHeight - TITLE_BAR_H - 2;
        renderContent(screen, mouseX, mouseY, partialTicks, contentX, contentY, contentW, contentH);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!isVisible()) return false;

        if (button == 0) {
            if (isInsideCloseButton(mouseX, mouseY)) {
                setOpen(false);
                return true;
            }
            if (draggable && isInsideTitleBar(mouseX, mouseY)) {
                dragging = true;
                dragOffsetX = mouseX - windowX;
                dragOffsetY = mouseY - windowY;
                return true;
            }
            if (isInsideWindow(mouseX, mouseY)) {
                handleContentClick(mouseX, mouseY, button);
                return true;
            }
        }
        return isInsideWindow(mouseX, mouseY);
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (!isVisible()) return false;
        if (isInsideWindow(mouseX, mouseY)) {
            return handleContentScroll(mouseX, mouseY, scroll);
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        if (!open) return false;
        if (keyCode == 1) {
            setOpen(false);
            return true;
        }
        return handleWindowKey(c, keyCode);
    }

    public boolean mouseDragged(int mouseX, int mouseY) {
        if (!open || !dragging) return false;
        windowX = mouseX - dragOffsetX;
        windowY = mouseY - dragOffsetY;
        return true;
    }

    public boolean mouseReleased(int mouseX, int mouseY) {
        dragging = false;
        return isInsideWindow(mouseX, mouseY);
    }

    // ==== 子类必须实现 ====

    protected abstract String getWindowId();

    protected abstract String getTitle();

    protected abstract int getDefaultWidth();

    protected abstract int getDefaultHeight();

    protected abstract void renderContent(GuiScreen screen, int mouseX, int mouseY, float partialTicks, int contentX,
        int contentY, int contentW, int contentH);

    protected abstract void handleContentClick(int mouseX, int mouseY, int button);

    // ==== 子类可覆盖 ====

    protected boolean canShowWindow() {
        return true;
    }

    protected boolean handleContentScroll(int mouseX, int mouseY, int scroll) {
        return false;
    }

    protected boolean handleWindowKey(char c, int keyCode) {
        return false;
    }

    protected void onClose() {}

    // ==== 内部实现 ====

    private void ensureBounds() {
        if (windowWidth <= 0 || windowHeight <= 0) {
            windowWidth = Math.max(MIN_W, getDefaultWidth());
            windowHeight = Math.max(MIN_H, getDefaultHeight());
            windowX = 10;
            windowY = 60;
        }
    }

    private void clampToScreen(GuiScreen screen) {
        if (screen == null) return;
        windowX = Math.max(SCREEN_MARGIN, Math.min(windowX, screen.width - windowWidth - SCREEN_MARGIN));
        windowY = Math.max(SCREEN_MARGIN, Math.min(windowY, screen.height - windowHeight - SCREEN_MARGIN));
    }

    private boolean isInsideWindow(int mx, int my) {
        return mx >= windowX && mx < windowX + windowWidth && my >= windowY && my < windowY + windowHeight;
    }

    private boolean isInsideTitleBar(int mx, int my) {
        return mx >= windowX && mx < windowX + windowWidth - CLOSE_BTN_W - 2
            && my >= windowY
            && my < windowY + TITLE_BAR_H;
    }

    private boolean isInsideCloseButton(int mx, int my) {
        int cx = windowX + windowWidth - CLOSE_BTN_W - 4;
        int cy = windowY + 1;
        return mx >= cx && mx < cx + CLOSE_BTN_W && my >= cy && my < cy + CLOSE_BTN_W;
    }

    private void renderWindowFrame(GuiScreen screen, int mx, int my) {
        FontRenderer fr = screen.mc.fontRenderer;

        drawRect(windowX + 2, windowY + 2, windowX + windowWidth + 2, windowY + windowHeight + 2, 0x44000000);

        drawRect(windowX, windowY, windowX + windowWidth, windowY + windowHeight, 0xE0101820);

        int borderColor = mouseHovering ? 0xFF4A6A8A : 0xFF314055;
        drawHorizontalLine(windowX, windowX + windowWidth - 1, windowY, borderColor);
        drawHorizontalLine(windowX, windowX + windowWidth - 1, windowY + windowHeight - 1, borderColor);
        drawVerticalLine(windowX, windowY, windowY + windowHeight - 1, borderColor);
        drawVerticalLine(windowX + windowWidth - 1, windowY, windowY + windowHeight - 1, borderColor);

        drawRect(windowX + 1, windowY + 1, windowX + windowWidth - 1, windowY + TITLE_BAR_H, 0xCC1A2433);
        drawHorizontalLine(windowX + 1, windowX + windowWidth - 2, windowY + TITLE_BAR_H, 0xFF314055);

        String title = truncate(fr, getTitle(), windowWidth - CLOSE_BTN_W - 14);
        fr.drawString(title, windowX + 6, windowY + (TITLE_BAR_H - fr.FONT_HEIGHT) / 2 + 1, 0xE0E8F0);

        int cx = windowX + windowWidth - CLOSE_BTN_W - 4;
        int cy = windowY + 1;
        boolean hoverClose = isInsideCloseButton(mx, my);
        int closeBg = hoverClose ? 0xCC552222 : 0x44222222;
        drawRect(cx, cy, cx + CLOSE_BTN_W, cy + CLOSE_BTN_W, closeBg);
        int closeColor = hoverClose ? 0xFFFF5555 : 0xFF888888;
        fr.drawString("x", cx + (CLOSE_BTN_W - fr.getStringWidth("x")) / 2, cy + 1, closeColor);
    }

    // ==== GL11 渲染辅助 ====

    protected static void drawRect(int x1, int y1, int x2, int y2, int color) {
        if (x1 >= x2 || y1 >= y2) return;
        float a = ((color >> 24) & 0xFF) / 255F;
        float r = ((color >> 16) & 0xFF) / 255F;
        float g = ((color >> 8) & 0xFF) / 255F;
        float b = (color & 0xFF) / 255F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x1, y2, 0);
        tess.addVertex(x2, y2, 0);
        tess.addVertex(x2, y1, 0);
        tess.addVertex(x1, y1, 0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    protected static void drawHorizontalLine(int x1, int x2, int y, int color) {
        drawRect(x1, y, x2 + 1, y + 1, color);
    }

    protected static void drawVerticalLine(int x, int y1, int y2, int color) {
        drawRect(x, y1, x + 1, y2 + 1, color);
    }

    private static String truncate(FontRenderer fr, String s, int maxW) {
        if (fr.getStringWidth(s) <= maxW) return s;
        String dots = "...";
        int dotsW = fr.getStringWidth(dots);
        for (int i = s.length() - 1; i > 0; i--) {
            if (fr.getStringWidth(s.substring(0, i)) + dotsW <= maxW) {
                return s.substring(0, i) + dots;
            }
        }
        return dots;
    }

    @Override
    public void resetFrameState() {
        mouseHovering = false;
    }
}
