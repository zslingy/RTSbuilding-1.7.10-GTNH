package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;

/**
 * 快速建造窗口面板 — BUILD/DESTROY 模式切换、形状选择、填充模式、尺寸旋转控制。
 * 对齐原版 QuickBuildPanel，继承 RtsWindowPanel 获得窗口框架能力。
 */
public class QuickBuildPanel extends RtsWindowPanel {

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 190;
    private static final int ROW_H = 14;
    private static final int BTN_SPACING = 2;

    private static final String[] SHAPES = { "BLOCK", "LINE", "SQUARE", "WALL", "CIRCLE", "BOX" };
    private static final String[] FILLS = { "FILL", "HOLLOW", "SKELETON" };

    private final RtsClientState state;

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
        return PANEL_H;
    }

    public QuickBuildPanel() {
        this.state = RtsClientState.get();
    }

    @Override
    protected boolean canShowWindow() {
        return state.interaction.quickBuildActive;
    }

    @Override
    protected void renderContent(GuiScreen screen, int mouseX, int mouseY, float t, int cx, int cy, int cw, int ch) {
        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;

        int x = cx + 2;
        int y = cy + 2;
        int bi = 0;

        // === 形状标签 ===
        fr.drawString(StatCollector.translateToLocal("screen.rtsbuilding.quick_build.shape") + ":", x, y, 0xFFA0B0C0);

        y += ROW_H;

        // === 形状按钮 (6个, 2列) ===
        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (cw / 2);
            int by = y + row * (ROW_H + BTN_SPACING);
            boolean active = SHAPES[i].equalsIgnoreCase(ivm.quickBuildShape);
            String displayName = StatCollector.translateToLocal("screen.rtsbuilding.shape." + SHAPES[i].toLowerCase());
            renderTextBtn(screen, bx, by, cw / 2 - 4, displayName, active, mouseX, mouseY, bi++);
        }

        y += ((SHAPES.length + 1) / 2) * (ROW_H + BTN_SPACING) + 4;

        // === 圆柱切换 (仅 CIRCLE 形状) ===
        if ("circle".equalsIgnoreCase(ivm.quickBuildShape)) {
            String cylLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.cylinder");
            renderToggleBtn(screen, x, y, cw - 4, ivm.quickBuildCylinder, cylLabel, mouseX, mouseY, bi++);
            y += ROW_H + 4;
        }

        // === P1-1: 8向角度吸附开关 (仅 LINE/WALL 形状) ===
        if ("line".equalsIgnoreCase(ivm.quickBuildShape) || "wall".equalsIgnoreCase(ivm.quickBuildShape)) {
            String snapLabel = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.snap_8dir");
            if (snapLabel == null || snapLabel.startsWith("screen.")) snapLabel = "8-Direction Snap";
            renderToggleBtn(screen, x, y, cw - 4, ivm.lineSnap8Direction, snapLabel, mouseX, mouseY, bi++);
            y += ROW_H + 4;
        }

        // === 填充标签 ===
        fr.drawString(StatCollector.translateToLocal("screen.rtsbuilding.quick_build.fill") + ":", x, y, 0xFFA0B0C0);

        y += ROW_H;

        // === 填充按钮 ===
        for (int i = 0; i < FILLS.length; i++) {
            int bx = x + i * (cw / 3);
            boolean active = FILLS[i].equalsIgnoreCase(ivm.quickBuildFill);
            String fillName = StatCollector.translateToLocal("screen.rtsbuilding.fill." + FILLS[i].toLowerCase());
            renderTextBtn(screen, bx, y, cw / 3 - 2, fillName, active, mouseX, mouseY, bi++);
        }

        y += ROW_H + 6;

        // === 旋转 ===
        String rotText = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.rotation") + ": "
            + (ivm.quickBuildRotation * 15)
            + "\u00B0";
        fr.drawString(rotText, x, y, 0xFFD0D0A0);

        // 旋转微调按钮
        int rotBtnX = x + fr.getStringWidth(rotText) + 6;
        boolean hoverMinus = checkRect(rotBtnX, y, 20, ROW_H, mouseX, mouseY);
        boolean hoverPlus = checkRect(rotBtnX + 24, y, 20, ROW_H, mouseX, mouseY);
        fr.drawString("[-]", rotBtnX, y, hoverMinus ? 0xFFFF5555 : 0xFFCC4444);
        fr.drawString("[+]", rotBtnX + 24, y, hoverPlus ? 0xFF55FF55 : 0xFF44CC44);
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

        y += ROW_H;

        // 形状按钮 (2列, 6个)
        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (cw / 2);
            int by = y + row * (ROW_H + BTN_SPACING);
            if (checkRect(bx, by, cw / 2 - 4, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildShape = SHAPES[i].toLowerCase();
                return;
            }
        }

        y += ((SHAPES.length + 1) / 2) * (ROW_H + BTN_SPACING) + 4;

        // 圆柱切换 (仅 CIRCLE)
        if ("circle".equalsIgnoreCase(ivm.quickBuildShape)) {
            if (checkRect(x, y, cw - 4, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildCylinder = !ivm.quickBuildCylinder;
                return;
            }
            y += ROW_H + 4;
        }

        // P1-1: 8向角度吸附开关 (仅 LINE/WALL)
        if ("line".equalsIgnoreCase(ivm.quickBuildShape) || "wall".equalsIgnoreCase(ivm.quickBuildShape)) {
            if (checkRect(x, y, cw - 4, ROW_H, mouseX, mouseY)) {
                ivm.lineSnap8Direction = !ivm.lineSnap8Direction;
                return;
            }
            y += ROW_H + 4;
        }

        y += ROW_H;

        // 填充按钮
        for (int i = 0; i < FILLS.length; i++) {
            int bx = x + i * (cw / 3);
            if (checkRect(bx, y, cw / 3 - 2, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildFill = FILLS[i].toLowerCase();
                return;
            }
        }

        y += ROW_H + 6;

        // 旋转 +/- 按钮
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String rotText = StatCollector.translateToLocal("screen.rtsbuilding.quick_build.rotation") + ": "
            + (ivm.quickBuildRotation * 15)
            + "\u00B0";
        int rotBtnX = x + fr.getStringWidth(rotText) + 6;
        if (checkRect(rotBtnX, y, 20, ROW_H, mouseX, mouseY)) {
            ivm.quickBuildRotation = (ivm.quickBuildRotation - 1 + 24) % 24;
            return;
        }
        if (checkRect(rotBtnX + 24, y, 20, ROW_H, mouseX, mouseY)) {
            ivm.quickBuildRotation = (ivm.quickBuildRotation + 1) % 24;
            return;
        }
    }

    private boolean checkRect(int rx, int ry, int rw, int rh, int mx, int my) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void renderToggleBtn(GuiScreen screen, int bx, int by, int bw, boolean active, String label, int mx, int my,
        int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);

        int bgColor = active ? 0xFF28553E : (hover ? 0xFF2A3A4A : 0xFF1A2330);
        int borderColor = active ? 0xFF5FE36C : (hover ? 0xFF647B92 : 0xFF314055);
        int textColor = active ? 0xFFD8FFE0 : 0xFFB0C0D0;

        drawRect(bx, by, bx + bw, by + ROW_H, borderColor);
        drawRect(bx + 1, by + 1, bx + bw - 1, by + ROW_H - 1, bgColor);
        fr.drawString(label, bx + (bw - fr.getStringWidth(label)) / 2, by + 2, textColor);
    }

    private void renderTextBtn(GuiScreen screen, int bx, int by, int bw, String label, boolean active, int mx, int my,
        int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean hover = checkRect(bx, by, bw, ROW_H, mx, my);
        int color = active ? 0xFFCCCC44 : (hover ? 0xFF888888 : 0xFF666666);
        fr.drawString(label, bx + 2, by + 1, color);
    }

    private void renderSmallStepper(GuiScreen screen, int bx, int by, int bw, String label, int val, int mx, int my,
        int baseIdx, int dim) {
        FontRenderer fr = screen.mc.fontRenderer;
        fr.drawString(label + ":", bx, by, 0xFF888888);
        int valX = bx + 14;
        fr.drawString(String.valueOf(val), valX, by, 0xFFCCCCAA);

        int btnW = bw / 3;
        int minusX = valX + fr.getStringWidth(String.valueOf(val)) + 4;
        int plusX = minusX + btnW + 2;

        boolean hoverMinus = checkRect(minusX, by, btnW, ROW_H, mx, my);
        boolean hoverPlus = checkRect(plusX, by, btnW, ROW_H, mx, my);

        int mCol = hoverMinus ? 0xFFFF5555 : 0xFFCC4444;
        int pCol = hoverPlus ? 0xFF55FF55 : 0xFF44CC44;
        fr.drawString("-", minusX, by, mCol);
        fr.drawString("+", plusX, by, pCol);
    }
}
