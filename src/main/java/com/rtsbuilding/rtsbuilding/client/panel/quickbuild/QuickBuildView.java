package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 快速建造面板 — 形状/填充/旋转参数选择。
 * 替代原 QuickBuildPanel / UltiminePanel / ShapeController。
 */
public class QuickBuildView implements IRtsPanel {

    private static final String PANEL_NAME = "quick_build";
    private static final int TOP_OFFSET = 55;
    private static final int PANEL_W = 200;

    private static final String[] SHAPES = { "BLOCK", "PLANE", "CUBE", "SPHERE", "CYLINDER", "PYRAMID", "LINE" };
    private static final String[] FILLS = { "FILL", "HOLLOW", "WIREFRAME" };

    private final RtsClientState state;
    private int qbX, qbY, qbH;
    private int hoveredShape = -1;
    private int hoveredFill = -1;

    public QuickBuildView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(qbX, qbY, PANEL_W, qbH);
    }

    @Override
    public boolean isVisible() {
        return state.interaction.quickBuildActive || state.interaction.ultimineActive;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;

        qbX = 10;
        qbY = TOP_OFFSET;

        // Bug2修复：连锁挖掘面板
        if (ivm.ultimineActive) {
            renderUltimineControls(fr, mouseX, mouseY, ivm);
            return;
        }

        qbH = (SHAPES.length + FILLS.length + 5) * 14;

        // 标题
        fr.drawString("Quick Build", qbX, qbY - 12, 0xFFCCCC44);

        // 形状
        fr.drawString("Shape:", qbX, qbY, 0xFFAAAAAA);
        for (int i = 0; i < SHAPES.length; i++) {
            int sy = qbY + 14 + i * 14;
            boolean active = SHAPES[i].equalsIgnoreCase(ivm.quickBuildShape);
            int color = active ? 0xFFCCCC44 : (i == hoveredShape ? 0xFF888888 : 0xFF666666);
            fr.drawString(SHAPES[i], qbX + 8, sy, color);
        }

        // 填充
        int fillY = qbY + 14 + SHAPES.length * 14 + 6;
        fr.drawString("Fill:", qbX, fillY, 0xFFAAAAAA);
        for (int i = 0; i < FILLS.length; i++) {
            int sy = fillY + 14 + i * 14;
            boolean active = FILLS[i].equalsIgnoreCase(ivm.quickBuildFill);
            int color = active ? 0xFFCCCC44 : (i == hoveredFill ? 0xFF888888 : 0xFF666666);
            fr.drawString(FILLS[i], qbX + 8, sy, color);
        }

        // 尺寸
        int sizeY = fillY + 14 + FILLS.length * 14 + 6;
        fr.drawString(
            String.format("Size: %d x %d x %d", ivm.quickBuildSizeX, ivm.quickBuildSizeY, ivm.quickBuildSizeZ),
            qbX,
            sizeY,
            0xFFCCCCAA);

        // 旋转
        fr.drawString("Rotation: " + ivm.quickBuildRotation, qbX, sizeY + 14, 0xFFCCCCAA);

        // 范围破坏
        if (ivm.areaDestroyActive) {
            fr.drawString(
                String
                    .format("Destroy: %d x %d x %d", ivm.areaDestroySizeX, ivm.areaDestroySizeY, ivm.areaDestroySizeZ),
                qbX,
                sizeY + 28,
                0xFFCC4444);
        }
    }

    /** Bug2修复：连锁挖掘控件 */
    private void renderUltimineControls(FontRenderer fr, int mouseX, int mouseY, InteractionViewModel ivm) {
        qbH = 10 * 14;
        int y = qbY;

        fr.drawString(StatCollector.translateToLocal("screen.rtsbuilding.ultimine.title"), qbX, y - 12, 0xFFCC4444);

        // 模式
        fr.drawString(StatCollector.translateToLocal("screen.rtsbuilding.ultimine.mode_chain"), qbX, y, 0xFFAAAAAA);

        // 限制数值 + 加减按钮
        int limitY = y + 16;
        fr.drawString(
            StatCollector.translateToLocalFormatted("screen.rtsbuilding.ultimine.limit", ivm.ultimineLimit),
            qbX,
            limitY,
            0xFFCCCC44);

        // [-] 和 [+] 按钮
        int btnY = limitY + 16;
        String minusLabel = "[-]";
        String plusLabel = "[+]";
        int minusColor = (mouseX >= qbX && mouseX <= qbX + 30 && mouseY >= btnY && mouseY <= btnY + 14) ? 0xFFFF5555
            : 0xFFCC4444;
        int plusColor = (mouseX >= qbX + 40 && mouseX <= qbX + 70 && mouseY >= btnY && mouseY <= btnY + 14) ? 0xFF55FF55
            : 0xFF44CC44;
        fr.drawString(minusLabel, qbX, btnY, minusColor);
        fr.drawString(plusLabel, qbX + 40, btnY, plusColor);

        // 提示
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.ultimine.mine_hint"),
            qbX,
            btnY + 18,
            0xFF666666);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0 || !isVisible()) return false;

        InteractionViewModel ivm = state.interaction;

        // Bug2修复：连锁挖掘控件点击
        if (ivm.ultimineActive) {
            int btnY = qbY + 16 + 16 + 16;
            // [-] 按钮
            if (mouseX >= qbX && mouseX <= qbX + 30 && mouseY >= btnY && mouseY <= btnY + 14) {
                ivm.ultimineLimit = Math.max(1, ivm.ultimineLimit - 16);
                return true;
            }
            // [+] 按钮
            if (mouseX >= qbX + 40 && mouseX <= qbX + 70 && mouseY >= btnY && mouseY <= btnY + 14) {
                ivm.ultimineLimit = Math.min(256, ivm.ultimineLimit + 16);
                return true;
            }
            return true;
        }

        // 形状点击
        int shapeStartY = qbY + 14;
        for (int i = 0; i < SHAPES.length; i++) {
            int sy = shapeStartY + i * 14;
            if (mouseX >= qbX && mouseX <= qbX + PANEL_W && mouseY >= sy && mouseY <= sy + 14) {
                ivm.quickBuildShape = SHAPES[i].toLowerCase();
                return true;
            }
        }

        // 填充点击
        int fillStartY = shapeStartY + SHAPES.length * 14 + 6 + 14;
        for (int i = 0; i < FILLS.length; i++) {
            int sy = fillStartY + i * 14;
            if (mouseX >= qbX && mouseX <= qbX + PANEL_W && mouseY >= sy && mouseY <= sy + 14) {
                ivm.quickBuildFill = FILLS[i].toLowerCase();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        return false;
    }

    @Override
    public void resetFrameState() {
        hoveredShape = -1;
        hoveredFill = -1;
    }
}
