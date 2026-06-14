package com.rtsbuilding.rtsbuilding.client.panel.ultimine;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;

/**
 * 连锁挖掘窗口面板 — 进度显示、限制输入+滑块、CHAIN/AREA 模式切换、形状选择。
 * 对齐原版 UltiminePanel，继承 RtsWindowPanel 获得窗口框架能力。
 */
public class UltiminePanel extends RtsWindowPanel {

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 184;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 256;
    private static final int ROW_H = 14;

    private static final String[] AREA_SHAPES = { "BLOCK", "LINE", "SQUARE", "WALL", "CIRCLE", "BOX" };

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
        return state.interaction.ultimineActive;
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

        // === 限制值 ===
        int limit = ivm.ultimineLimit;
        fr.drawString(
            StatCollector.translateToLocalFormatted("screen.rtsbuilding.ultimine.limit", limit),
            x,
            y,
            0xFFCCCC44);
        y += ROW_H + 2;

        // === 滑块 ===
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

        y += ROW_H + 16;

        // === 模式切换 CHAIN / AREA ===
        boolean chainMode = !ivm.areaDestroyActive;
        int toggleW = (cw - 12) / 2;
        renderModeBtn(
            screen,
            x,
            y,
            toggleW,
            StatCollector.translateToLocal("screen.rtsbuilding.ultimine.mode_chain"),
            chainMode,
            mouseX,
            mouseY);
        renderModeBtn(
            screen,
            x + toggleW + 4,
            y,
            toggleW,
            StatCollector.translateToLocal("screen.rtsbuilding.ultimine.mode_area"),
            !chainMode,
            mouseX,
            mouseY);

        y += ROW_H + 8;

        // === AREA 模式: 形状选择 ===
        if (!chainMode) {
            fr.drawString(
                StatCollector.translateToLocal("screen.rtsbuilding.quick_build.shape") + ":",
                x,
                y,
                0xFFA0B0C0);
            y += ROW_H;
            int shapeW = (cw - 12) / 3;
            for (int i = 0; i < AREA_SHAPES.length; i++) {
                int col = i % 3;
                int row = i / 3;
                int sx = x + col * (shapeW + 2);
                int sy = y + row * (ROW_H + 2);
                boolean active = AREA_SHAPES[i].equalsIgnoreCase(ivm.quickBuildShape);
                renderShapeBtn(screen, sx, sy, shapeW, AREA_SHAPES[i], active, mouseX, mouseY);
            }
        }
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

        // 模式切换
        int modeY = trackY + ROW_H + 16;
        int toggleW = (cw - 12) / 2;
        if (checkRect(x, modeY, toggleW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = false;
            return;
        }
        if (checkRect(x + toggleW + 4, modeY, toggleW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = true;
            return;
        }

        // AREA 模式: 形状按钮
        if (ivm.areaDestroyActive) {
            int shapeY = modeY + ROW_H + 8 + ROW_H;
            int shapeW = (cw - 12) / 3;
            for (int i = 0; i < AREA_SHAPES.length; i++) {
                int col = i % 3;
                int row = i / 3;
                int sx = x + col * (shapeW + 2);
                int sy = shapeY + row * (ROW_H + 2);
                if (checkRect(sx, sy, shapeW, ROW_H, mouseX, mouseY)) {
                    ivm.quickBuildShape = AREA_SHAPES[i].toLowerCase();
                    return;
                }
            }
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

    private void renderModeBtn(GuiScreen screen, int bx, int by, int bw, String label, boolean active, int mx, int my) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int bg = active ? 0xFF28553E : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int border = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF314055);
        int textColor = active ? 0xFFD8FFE0 : (hover ? 0xFFD0D8E0 : 0xFF8A9AAA);

        drawRect(bx, by, bx + bw, by + ROW_H, border);
        drawRect(bx + 1, by + 1, bx + bw - 1, by + ROW_H - 1, bg);
        fr.drawString(label, bx + (bw - fr.getStringWidth(label)) / 2, by + 2, textColor);
    }

    private void renderShapeBtn(GuiScreen screen, int bx, int by, int bw, String label, boolean active, int mx,
        int my) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int bg = active ? 0xFF28553E : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int border = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF314055);
        int textColor = active ? 0xFFD8FFE0 : (hover ? 0xFFD0D8E0 : 0xFF8A9AAA);

        drawRect(bx, by, bx + bw, by + ROW_H, border);
        drawRect(bx + 1, by + 1, bx + bw - 1, by + ROW_H - 1, bg);
        fr.drawString(label, bx + 2, by + 2, textColor);
    }
}
