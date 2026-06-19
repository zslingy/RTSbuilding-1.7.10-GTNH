package com.rtsbuilding.rtsbuilding.client.panel.ultimine;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;

/**
 * 连锁挖掘窗口面板 — 进度显示、上限滑块。
 * 范围破坏(area destroy)形状选择已移至 QuickBuildPanel。
 */
public class UltiminePanel extends RtsWindowPanel {

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 170;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 256;
    private static final int ROW_H = 14;

    private final RtsClientState state;
    private boolean sliderDragging;

    @Override
    protected String getWindowId() {
        return "ultimine_panel";
    }

    @Override
    protected String getTitle() {
        return StatCollector.translateToLocal("screen.rtsbuilding.ultimine.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    public UltiminePanel() {
        this.state = RtsClientState.get();
    }

    @Override
    protected boolean canShowWindow() {
        return state.interaction.ultimineActive && !state.interaction.areaDestroyActive;
    }

    @Override
    protected void renderContent(GuiScreen screen, int mouseX, int mouseY, float t, int cx, int cy, int cw, int ch) {
        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;
        int x = cx + 2;
        int y = cy + 2;

        // === 进度条 ===
        String progressLabel = StatCollector.translateToLocal("screen.rtsbuilding.ultimine.ready");
        int progressColor = 0xFFAFC0D3;
        float progressFraction = 0F;
        if (ivm.ultimineProgressProcessed >= 0 && ivm.ultimineProgressTotal > 0) {
            progressFraction = (float) ivm.ultimineProgressProcessed / ivm.ultimineProgressTotal;
            progressLabel = StatCollector.translateToLocalFormatted(
                "screen.rtsbuilding.ultimine.breaking_progress",
                ivm.ultimineProgressProcessed,
                ivm.ultimineProgressTotal);
            progressColor = 0xFFB8FFB8;
        } else if (ivm.mineProgressX >= 0 && ivm.mineProgressStage >= 0) {
            progressFraction = ivm.mineProgressStage / 100F;
            progressLabel = StatCollector
                .translateToLocalFormatted("screen.rtsbuilding.ultimine.breaking", ivm.mineProgressStage + "%");
            progressColor = 0xFFB8FFB8;
        }

        fr.drawString(progressLabel, x, y, progressColor);
        y += ROW_H + 2;

        int barW = cw - 8;
        drawRect(x, y, x + barW, y + 10, 0xAA101820);
        drawRect(x + 1, y + 1, x + barW - 1, y + 9, 0xFF1A2330);
        if (progressFraction > 0F) {
            int fillW = Math.max(1, (int) (progressFraction * (barW - 2)));
            drawRect(x + 2, y + 2, x + 2 + fillW, y + 8, 0xFF78B28C);
        }

        y += 16;

        // === 连锁上限滑块 ===
        int limit = ivm.ultimineLimit;
        fr.drawString(
            StatCollector.translateToLocalFormatted("screen.rtsbuilding.ultimine.limit", limit),
            x,
            y,
            0xFFCCCC44);
        y += ROW_H + 2;

        int trackX = x;
        int trackW = cw - 8;
        int knobX = trackX + (int) Math.round((limit - MIN_LIMIT) / (double) (MAX_LIMIT - MIN_LIMIT) * trackW);

        boolean hoverSlider = checkRect(trackX, y - 4, trackW, ROW_H, mouseX, mouseY);

        drawRect(trackX, y + 3, trackX + trackW, y + 7, 0xFF0D1117);
        drawRect(trackX + 1, y + 4, trackX + trackW - 1, y + 6, 0xFF314055);
        drawRect(trackX, y + 4, knobX, y + 6, 0xFF5FE36C);

        int knobColor = sliderDragging ? 0xFF8AFF8A : (hoverSlider ? 0xFF6AFF7A : 0xFF5FE36C);
        drawRect(knobX - 3, y - 1, knobX + 4, y + 10, knobColor);

        fr.drawString(String.valueOf(MIN_LIMIT), trackX, y + 9, 0xFFB0C0D0);
        fr.drawString(
            String.valueOf(MAX_LIMIT),
            trackX + trackW - fr.getStringWidth(String.valueOf(MAX_LIMIT)),
            y + 9,
            0xFFB0C0D0);
    }

    @Override
    protected void handleContentClick(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        InteractionViewModel ivm = state.interaction;
        int cx = windowX + 2;
        int cy = windowY + 16 + 2;
        int cw = windowWidth - 4;
        int x = cx + 2;
        int y = cy + 2;

        // 滑块区域
        int trackY = y + ROW_H + 2 + 16 + ROW_H + 2;
        int trackX = x;
        int trackW = cw - 8;
        if (checkRect(trackX, trackY - 4, trackW, ROW_H, mouseX, mouseY)) {
            sliderDragging = true;
            updateLimitFromSlider(mouseX, ivm);
            return;
        }
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY) {
        if (sliderDragging) {
            updateLimitFromSlider(mouseX, state.interaction);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY) {
        sliderDragging = false;
        return super.mouseReleased(mouseX, mouseY);
    }

    private void updateLimitFromSlider(int mouseX, InteractionViewModel ivm) {
        int trackX = windowX + 2 + 2;
        int trackW = windowWidth - 4 - 8;
        double fraction = (mouseX - trackX) / (double) trackW;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        ivm.ultimineLimit = MIN_LIMIT + (int) Math.round(fraction * (MAX_LIMIT - MIN_LIMIT));
    }

    private boolean checkRect(int rx, int ry, int rw, int rh, int mx, int my) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }
}
