package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;

/**
 * 快速建造窗口面板 — BUILD/DESTROY 模式切换、形状选择、填充模式、尺寸旋转控制。
 * 对齐原版 QuickBuildPanel，继承 RtsWindowPanel 获得窗口框架能力。
 */
public class QuickBuildPanel extends RtsWindowPanel {

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 14;
    private static final int BTN_SPACING = 2;

    private static final String[] SHAPES = { "BLOCK", "LINE", "SQUARE", "WALL", "CIRCLE", "BOX" };
    private static final String[] FILLS = { "FILL", "HOLLOW", "WIREFRAME" };

    private final RtsClientState state;
    private int hoveredBtn = -1;
    private int[] btnYCache;

    private boolean buildMode = true;

    @Override
    protected String getWindowId() {
        return "quick_build_panel";
    }

    @Override
    protected String getTitle() {
        return "Quick Build";
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
        btnYCache = new int[32];

        int x = cx + 2;
        int y = cy + 2;
        int bi = 0;

        // === 行1: BUILD / DESTROY 切换 ===
        buildMode = !ivm.areaDestroyActive;
        int toggleW = (cw - 12) / 2;
        renderToggleBtn(screen, x, y, toggleW, true, "BUILD", mouseX, mouseY, bi++);
        renderToggleBtn(screen, x + toggleW + 4, y, toggleW, false, "DESTROY", mouseX, mouseY, bi++);

        y += ROW_H + 6;

        // === 行2: 形状标签 ===
        fr.drawString("Shape:", x, y, 0xFFA0B0C0);

        y += ROW_H;

        // === 行3-5: 形状按钮 (6个, 2列) ===
        for (int i = 0; i < SHAPES.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = x + col * (cw / 2);
            int by = y + row * (ROW_H + BTN_SPACING);
            boolean active = SHAPES[i].equalsIgnoreCase(ivm.quickBuildShape);
            renderTextBtn(screen, bx, by, cw / 2 - 4, SHAPES[i], active, mouseX, mouseY, bi++);
        }

        y += ((SHAPES.length + 1) / 2) * (ROW_H + BTN_SPACING) + 4;

        // === 填充标签 ===
        fr.drawString("Fill:", x, y, 0xFFA0B0C0);

        y += ROW_H;

        // === 填充按钮 ===
        for (int i = 0; i < FILLS.length; i++) {
            int bx = x + i * (cw / 3);
            boolean active = FILLS[i].equalsIgnoreCase(ivm.quickBuildFill);
            renderTextBtn(screen, bx, y, cw / 3 - 2, FILLS[i], active, mouseX, mouseY, bi++);
        }

        y += ROW_H + 6;

        // === 尺寸 ===
        String sizeText = String
            .format("Size: %d x %d x %d", ivm.quickBuildSizeX, ivm.quickBuildSizeY, ivm.quickBuildSizeZ);
        fr.drawString(sizeText, x, y, 0xFFD0D0A0);

        // 尺寸微调按钮
        y += ROW_H;
        String[] dims = { "X", "Y", "Z" };
        int[] vals = { ivm.quickBuildSizeX, ivm.quickBuildSizeY, ivm.quickBuildSizeZ };
        int dimBtnW = (cw - 16) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = x + i * (dimBtnW + 2);
            renderSmallStepper(screen, bx, y, dimBtnW, dims[i], vals[i], mouseX, mouseY, bi, i);
            bi += 2;
        }

        y += ROW_H + 4;

        // === 旋转 ===
        String rotText = "Rotation: " + (ivm.quickBuildRotation * 90) + "°";
        fr.drawString(rotText, x, y, 0xFFD0D0A0);

        // 范围破坏信息
        if (ivm.areaDestroyActive) {
            y += ROW_H + 2;
            fr.drawString(
                String
                    .format("Destroy: %d x %d x %d", ivm.areaDestroySizeX, ivm.areaDestroySizeY, ivm.areaDestroySizeZ),
                x,
                y,
                0xFFFF4444);
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

        // BUILD/DESTROY 切换
        int toggleW = (cw - 12) / 2;
        if (checkRect(x, y, toggleW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = false;
            buildMode = true;
            return;
        }
        if (checkRect(x + toggleW + 4, y, toggleW, ROW_H, mouseX, mouseY)) {
            ivm.areaDestroyActive = true;
            buildMode = false;
            return;
        }

        y += ROW_H + 6 + ROW_H;

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

        y += ((SHAPES.length + 1) / 2) * (ROW_H + BTN_SPACING) + 4 + ROW_H;

        // 填充按钮
        for (int i = 0; i < FILLS.length; i++) {
            int bx = x + i * (cw / 3);
            if (checkRect(bx, y, cw / 3 - 2, ROW_H, mouseX, mouseY)) {
                ivm.quickBuildFill = FILLS[i].toLowerCase();
                return;
            }
        }

        y += ROW_H + 6 + ROW_H;

        // 尺寸 +/- 按钮
        int dimBtnW = (cw - 16) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = x + i * (dimBtnW + 2);
            if (checkRect(bx, y, dimBtnW / 3, ROW_H, mouseX, mouseY)) {
                adjustSize(ivm, i, -1);
                return;
            }
            if (checkRect(bx + dimBtnW - dimBtnW / 3, y, dimBtnW / 3, ROW_H, mouseX, mouseY)) {
                adjustSize(ivm, i, +1);
                return;
            }
        }
    }

    private void adjustSize(InteractionViewModel ivm, int dim, int delta) {
        switch (dim) {
            case 0:
                ivm.quickBuildSizeX = Math.max(1, Math.min(32, ivm.quickBuildSizeX + delta));
                break;
            case 1:
                ivm.quickBuildSizeY = Math.max(1, Math.min(32, ivm.quickBuildSizeY + delta));
                break;
            case 2:
                ivm.quickBuildSizeZ = Math.max(1, Math.min(32, ivm.quickBuildSizeZ + delta));
                break;
        }
    }

    private boolean checkRect(int rx, int ry, int rw, int rh, int mx, int my) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void renderToggleBtn(GuiScreen screen, int bx, int by, int bw, boolean isBuild, String label, int mx,
        int my, int idx) {
        FontRenderer fr = screen.mc.fontRenderer;
        boolean active = isBuild == buildMode;
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
