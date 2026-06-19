package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;

/**
 * 快速建造面板 — BUILD/DESTROY 模式切换、形状选择、填充模式、尺寸旋转。
 * 范围破坏(area destroy)形状选型在本面板，连锁破坏(chain)在上限控制由 UltiminePanel 负责。
 */
public class QuickBuildPanel extends RtsWindowPanel {

    private static final int PANEL_W = 180;
    private static final int PANEL_H_BUILD = 190;
    private static final int PANEL_H_DESTROY = 190;
    private static final int ROW_H = 14;
    private static final int SHAPE_ICON_SIZE = 12;
    private static final int BTN_SPACING = 2;
    private static final int RIGHT_COL_X = 88;

    private static final int SHAPE_SHEET_W = 450;
    private static final int SHAPE_SHEET_H = 900;
    private static final int SHAPE_STATE_H = 450;

    private static final String[] BUILD_SHAPES = { "BLOCK", "LINE", "SQUARE", "WALL", "CIRCLE", "BOX" };
    private static final String[] DESTROY_SHAPES = { "BLOCK", "LINE", "SQUARE", "WALL", "CIRCLE", "BOX" };
    private static final String[] FILLS = { "FILL", "HOLLOW", "SKELETON" };

    private final RtsClientState state;
    private int hoveredButtonIdx = -1;

    @Override
    protected String getWindowId() {
        return "quick_build_panel";
    }

    @Override
    protected String getTitle() {
        return StatCollector.translateToLocal("screen.rtsbuilding.quick_build.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        InteractionViewModel ivm = state.interaction;
        return ivm.areaDestroyActive ? PANEL_H_DESTROY : PANEL_H_BUILD;
    }

    public QuickBuildPanel() {
        this.state = RtsClientState.get();
    }

    @Override
    protected boolean canShowWindow() {
        return state.interaction.quickBuildActive
            || (state.interaction.ultimineActive && state.interaction.areaDestroyActive);
    }

    @Override
    protected void renderContent(GuiScreen screen, int mouseX, int mouseY, float t, int cx, int cy, int cw, int ch) {
        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;

        int x = cx + 2;
        int y = cy + 2;
        hoveredButtonIdx = -1;
        int bi = 0;

        // === BUILD/DESTROY 模式切换 ===
        String buildLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.mode_build");
        if (buildLabel == null || buildLabel.startsWith("screen.")) buildLabel = "BUILD";
        String destroyLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.mode_destroy");
        if (destroyLabel == null || destroyLabel.startsWith("screen.")) destroyLabel = "DESTROY";
        boolean isDestroy = ivm.areaDestroyActive;

        int modeBtnW = (cw - 4) / 2;
        renderModeBtn(screen, x, y, modeBtnW, buildLabel, !isDestroy, mouseX, mouseY, bi++);
        renderModeBtn(screen, x + modeBtnW + 2, y, modeBtnW, destroyLabel, isDestroy, mouseX, mouseY, bi++);
        y += ROW_H + 4;

        // === 形状标签 ===
        fr.drawString(StatCollector.translateToLocal("screen.rtsbuilding.quick_build.shape") + ":", x, y, 0xFFA0B0C0);
        y += ROW_H;

        // === 形状按钮 ===
        String[] shapes = isDestroy ? DESTROY_SHAPES : BUILD_SHAPES;
        String activeShape = ivm.quickBuildShape;
        int shapeStartY = y;

        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (RIGHT_COL_X / 2);
            int by = y + row * (ROW_H + BTN_SPACING);
            boolean active = shapes[i].equalsIgnoreCase(activeShape);
            String displayName = StatCollector.translateToLocal("screen.rtsbuilding.shape." + shapes[i].toLowerCase());
            renderShapeBtn(screen, bx, by, RIGHT_COL_X / 2 - 4, shapes[i], displayName, active, mouseX, mouseY, bi++);
        }

        // === 右侧列（仅 BUILD 模式显示填充/圆柱/吸附/旋转） ===
        if (!isDestroy) {
            int rx = cx + RIGHT_COL_X;
            int ry = cy + 2 + ROW_H + 4;

            if ("circle".equalsIgnoreCase(ivm.quickBuildShape)) {
                String cylLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.cylinder");
                renderToggleBtn(
                    screen,
                    rx,
                    ry,
                    cw - RIGHT_COL_X - 2,
                    ivm.quickBuildCylinder,
                    cylLabel,
                    mouseX,
                    mouseY,
                    bi++);
                ry += ROW_H + 4;
            }

            if ("line".equalsIgnoreCase(ivm.quickBuildShape) || "wall".equalsIgnoreCase(ivm.quickBuildShape)) {
                String snapLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.snap_8dir");
                if (snapLabel == null || snapLabel.startsWith("screen.")) snapLabel = "8-Dir Snap";
                renderToggleBtn(
                    screen,
                    rx,
                    ry,
                    cw - RIGHT_COL_X - 2,
                    ivm.lineSnap8Direction,
                    snapLabel,
                    mouseX,
                    mouseY,
                    bi++);
                ry += ROW_H + 4;
            }

            fr.drawString(
                StatCollector.translateToLocal("screen.rtsbuilding.quick_build.fill") + ":",
                rx,
                ry,
                0xFFA0B0C0);
            ry += ROW_H;
            for (int i = 0; i < FILLS.length; i++) {
                boolean active = FILLS[i].equalsIgnoreCase(ivm.quickBuildFill);
                String fillName = StatCollector.translateToLocal("screen.rtsbuilding.fill." + FILLS[i].toLowerCase());
                renderTextBtn(screen, rx, ry, cw - RIGHT_COL_X - 2, fillName, active, mouseX, mouseY, bi++);
                ry += ROW_H;
            }
            ry += 4;

            String rotText = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.rotation") + ":"
                + (ivm.quickBuildRotation * 15)
                + "\u00B0";
            fr.drawString(rotText, rx, ry, 0xFFD0D0A0);
            int rotBtnX = rx + fr.getStringWidth(rotText) + 6;
            renderRotBtn(fr, rotBtnX, ry, "-", mouseX, mouseY, bi++);
            renderRotBtn(fr, rotBtnX + 24, ry, "+", mouseX, mouseY, bi++);
        }

        // === DESTROY模式进度提示 ===
        if (isDestroy && ivm.ultimineProgressProcessed >= 0) {
            int footerY = cy + ch - ROW_H - 4;
            String progText = "Progress: " + ivm.ultimineProgressProcessed + " / " + ivm.ultimineProgressTotal;
            fr.drawString(progText, cx + 2, footerY, 0xFF88AA88);
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
        boolean isDestroy = ivm.areaDestroyActive;

        // BUILD/DESTROY 切换
        int modeBtnW = (cw - 4) / 2;
        if (checkRect(x, y, modeBtnW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = false;
            return;
        }
        if (checkRect(x + modeBtnW + 2, y, modeBtnW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = true;
            return;
        }
        y += ROW_H + 4 + ROW_H;

        // 形状按钮
        String[] shapes = isDestroy ? DESTROY_SHAPES : BUILD_SHAPES;
        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (RIGHT_COL_X / 2);
            int by = y + row * (ROW_H + BTN_SPACING);
            if (checkRect(bx, by, RIGHT_COL_X / 2 - 4, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildShape = shapes[i].toLowerCase();
                if (isDestroy) {
                    ivm.ultimineActive = true;
                    ivm.areaDestroyActive = true;
                }
                return;
            }
        }

        if (!isDestroy) {
            int rx = cx + RIGHT_COL_X;
            int ry = cy + 2 + ROW_H + 4;

            if ("circle".equalsIgnoreCase(ivm.quickBuildShape)) {
                if (checkRect(rx, ry, cw - RIGHT_COL_X - 2, ROW_H, mouseX, mouseY)) {
                    ivm.quickBuildCylinder = !ivm.quickBuildCylinder;
                    return;
                }
                ry += ROW_H + 4;
            }
            if ("line".equalsIgnoreCase(ivm.quickBuildShape) || "wall".equalsIgnoreCase(ivm.quickBuildShape)) {
                if (checkRect(rx, ry, cw - RIGHT_COL_X - 2, ROW_H, mouseX, mouseY)) {
                    ivm.lineSnap8Direction = !ivm.lineSnap8Direction;
                    return;
                }
                ry += ROW_H + 4;
            }

            ry += ROW_H;
            for (int i = 0; i < FILLS.length; i++) {
                if (checkRect(rx, ry, cw - RIGHT_COL_X - 2, ROW_H, mouseX, mouseY)) {
                    ivm.quickBuildFill = FILLS[i].toLowerCase();
                    return;
                }
                ry += ROW_H;
            }
            ry += 4;

            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            String rotText = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.rotation") + ":"
                + (ivm.quickBuildRotation * 15)
                + "\u00B0";
            int rotBtnX = rx + fr.getStringWidth(rotText) + 6;
            if (checkRect(rotBtnX, ry, 20, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildRotation = (ivm.quickBuildRotation - 1 + 24) % 24;
                return;
            }
            if (checkRect(rotBtnX + 24, ry, 20, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildRotation = (ivm.quickBuildRotation + 1) % 24;
                return;
            }
        }
    }

    // ======== 渲染辅助 ========

    private void renderModeBtn(GuiScreen screen, int bx, int by, int bw, String label, boolean active, int mx, int my,
        int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int bgColor = active ? 0xFF28553E : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int borderColor = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF314055);
        int textColor = active ? 0xFFD8FFE0 : 0xFFB0C0D0;
        Gui.drawRect(bx, by, bx + bw, by + ROW_H, borderColor);
        Gui.drawRect(bx + 1, by + 1, bx + bw - 1, by + ROW_H - 1, bgColor);
        fr.drawString(label, bx + (bw - fr.getStringWidth(label)) / 2, by + 2, textColor);
        if (hover) hoveredButtonIdx = idx;
    }

    private void renderToggleBtn(GuiScreen screen, int bx, int by, int bw, boolean active, String label, int mx, int my,
        int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int bgColor = active ? 0xFF28553E : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int borderColor = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF314055);
        int textColor = active ? 0xFFD8FFE0 : 0xFFB0C0D0;
        Gui.drawRect(bx, by, bx + bw, by + ROW_H, borderColor);
        Gui.drawRect(bx + 1, by + 1, bx + bw - 1, by + ROW_H - 1, bgColor);
        fr.drawString(label, bx + (bw - fr.getStringWidth(label)) / 2, by + 2, textColor);
        if (hover) hoveredButtonIdx = idx;
    }

    private void renderTextBtn(GuiScreen screen, int bx, int by, int bw, String label, boolean active, int mx, int my,
        int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int color = active ? 0xFFCCCC44 : (hover ? 0xFF888888 : 0xFF666666);
        fr.drawString(label, bx + 2, by + 1, color);
        if (hover) hoveredButtonIdx = idx;
    }

    private void renderShapeBtn(GuiScreen screen, int bx, int by, int bw, String shape, String label, boolean active,
        int mx, int my, int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int color = active ? 0xFFCCCC44 : (hover ? 0xFF888888 : 0xFF666666);
        int iconY = by + (ROW_H - SHAPE_ICON_SIZE) / 2;
        int vOffset = active ? SHAPE_STATE_H : 0;
        WindowButton.drawTexture(
            screen.mc,
            getShapeTexture(shape),
            bx + 1,
            iconY,
            SHAPE_ICON_SIZE,
            SHAPE_ICON_SIZE,
            0,
            vOffset,
            SHAPE_SHEET_W,
            SHAPE_STATE_H,
            SHAPE_SHEET_W,
            SHAPE_SHEET_H);
        fr.drawString(label, bx + SHAPE_ICON_SIZE + 4, by + 1, color);
        if (hover) hoveredButtonIdx = idx;
    }

    private void renderRotBtn(FontRenderer fr, int bx, int by, String label, int mx, int my, int idx) {
        boolean hover = checkRect(bx, by, 20, ROW_H, mx, my);
        int color = "-".equals(label) ? (hover ? 0xFFFF5555 : 0xFFCC4444) : (hover ? 0xFF55FF55 : 0xFF44CC44);
        fr.drawString("[" + label + "]", bx, by, color);
        if (hover) hoveredButtonIdx = idx;
    }

    private boolean checkRect(int rx, int ry, int rw, int rh, int mx, int my) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private static ResourceLocation getShapeTexture(String shape) {
        String textureName = shape.equalsIgnoreCase("BLOCK") ? "single" : shape.toLowerCase();
        return new ResourceLocation("rtsbuilding", "textures/gui/quickbuild/" + textureName + "_block.png");
    }
}
