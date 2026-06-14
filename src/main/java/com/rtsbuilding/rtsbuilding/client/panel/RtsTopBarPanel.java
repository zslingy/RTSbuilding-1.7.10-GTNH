package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;

/**
 * 顶部工具栏面板 -- 模式按钮、操作按钮、存储状态、状态栏。
 * 
 * Bug1修复 (2026-06-12):
 * - 新增 ULTIMINE/CHUNK_VIEW/GUIDE 功能按钮
 * - 齿轮按钮(GEAR)打开 RtsModConfigScreen 设置面板
 * - 添加顶栏下方两行状态栏渲染
 * 
 * 实现 IRtsPanel 协议，由 RtsInputRouter 自动处理 hit-test。
 * 1.7.10 原生渲染：Gui.drawRect + fontRendererObj.drawString。
 */
public class RtsTopBarPanel implements IRtsPanel {

    private static final String PANEL_NAME = "top_bar";
    private static final int BAR_HEIGHT = 22;
    private static final int ROW2_HEIGHT = 16;
    private static final int ROW3_HEIGHT = 16;
    private static final int TOTAL_HEIGHT = BAR_HEIGHT + ROW2_HEIGHT + ROW3_HEIGHT;
    private static final int STATUS_AREA_H = ROW2_HEIGHT + ROW3_HEIGHT;
    private static final int MODE_BUTTON_W = 80;
    private static final int ACTION_BUTTON_W = 26;
    private static final int BUTTON_SPACING = 2;
    private static final int BAR_COLOR_BG = 0xCC222222;
    private static final int BUTTON_COLOR = 0xCC444444;
    private static final int BUTTON_COLOR_ACTIVE = 0xCC6688CC;
    private static final int BUTTON_COLOR_HOVER = 0xCC666666;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR_ACTIVE = 0xFFFFFF44;

    private final RtsClientState state;

    private int barX, barY, barWidth;
    private int hoveredButton = -1;

    // Bug1修复：扩展按钮定义，增加连锁挖掘、区块显示、引导
    private enum BtnId {
        INTERACT,
        LINK_STORAGE,
        ROTATE,
        FUNNEL,
        QUICK_BUILD,
        ULTIMINE,
        CHUNK_VIEW,
        GUIDE,
        GEAR
    }

    private static final BtnId[] TOP_BUTTONS = { BtnId.INTERACT, BtnId.LINK_STORAGE, BtnId.ROTATE, BtnId.FUNNEL,
        BtnId.QUICK_BUILD, BtnId.ULTIMINE, BtnId.CHUNK_VIEW, BtnId.GUIDE, BtnId.GEAR };

    private static final String[] BTN_LABEL_KEYS = { "screen.rtsbuilding.mode.interact",
        "screen.rtsbuilding.mode.link_storage", "screen.rtsbuilding.mode.rotate", "screen.rtsbuilding.mode.funnel",
        "screen.rtsbuilding.mode.quick_build", "screen.rtsbuilding.mode.ultimine", "screen.rtsbuilding.mode.chunk_view",
        "screen.rtsbuilding.btn.guide", "screen.rtsbuilding.mode.gear" };

    private static final int MODE_BUTTON_COUNT = 4;

    // 缩放按钮
    private static final int ZOOM_BTN_BASE = 100;
    private static final float ZOOM_MIN = 0.5f;
    private static final float ZOOM_MAX = 3.0f;
    private static final float ZOOM_STEP = 0.25f;

    // 按钮图标
    private static final String ICON_QUICK_BUILD = "\u2692";
    private static final String ICON_ULTIMINE = "\u26CF";
    private static final String ICON_CHUNK_VIEW = "\u25A6";
    private static final String ICON_GUIDE = "\u2139";
    private static final String ICON_GEAR = "\u2699";

    public RtsTopBarPanel() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(barX, barY, barWidth, TOTAL_HEIGHT);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        int w = screen.width;
        barX = 0;
        barY = 0;
        barWidth = w;
        FontRenderer fr = screen.mc.fontRenderer;

        // 整体背景
        Gui.drawRect(barX, barY, barX + barWidth, barY + TOTAL_HEIGHT, BAR_COLOR_BG);

        // === Row 1: 按钮栏 ===
        int btnRowTop = barY + 2;
        int btnRowBottom = barY + BAR_HEIGHT - 2;
        int btnYCenter = barY + BAR_HEIGHT / 2 - 4;

        int btnX = 4;
        for (int i = 0; i < TOP_BUTTONS.length; i++) {
            BtnId id = TOP_BUTTONS[i];
            boolean isMode = (i < MODE_BUTTON_COUNT);
            int bw = isMode ? MODE_BUTTON_W : ACTION_BUTTON_W;

            boolean active = isButtonActive(id);

            int btnColor;
            if (active) {
                btnColor = BUTTON_COLOR_ACTIVE;
            } else if (i == hoveredButton) {
                btnColor = BUTTON_COLOR_HOVER;
            } else {
                btnColor = BUTTON_COLOR;
            }

            int btnRight = btnX + bw;
            Gui.drawRect(btnX, btnRowTop, btnRight, btnRowBottom, btnColor);

            int textColor = active ? TEXT_COLOR_ACTIVE : TEXT_COLOR;
            if (isMode) {
                String label = StatCollector.translateToLocal(BTN_LABEL_KEYS[i]);
                if (label == null || label.isEmpty() || label.startsWith("screen.")) {
                    label = id.name();
                }
                drawCenteredString(fr, label, btnX + bw / 2, btnYCenter, textColor);
            } else {
                String icon = getButtonIcon(id);
                drawCenteredString(fr, icon, btnX + bw / 2, btnYCenter, textColor);
            }

            btnX = btnRight + BUTTON_SPACING;
        }

        // 右侧缩放按钮
        String zoomLabel = String.format("%.0f%%", state.uiZoom * 100);
        int zoomLabelWidth = fr.getStringWidth(zoomLabel);
        int zoomX = w - zoomLabelWidth - 60;
        int minusBtnX = zoomX;
        int plusBtnX = zoomX + zoomLabelWidth + 30;

        fr.drawString(zoomLabel, zoomX + 14, btnYCenter, 0xFFCCCCCC);

        int minusColor = (hoveredButton == ZOOM_BTN_BASE) ? BUTTON_COLOR_HOVER : BUTTON_COLOR;
        Gui.drawRect(minusBtnX, btnRowTop, minusBtnX + 10, btnRowBottom, minusColor);
        drawCenteredString(fr, "-", minusBtnX + 5, btnYCenter, TEXT_COLOR);

        int plusColor = (hoveredButton == ZOOM_BTN_BASE + 1) ? BUTTON_COLOR_HOVER : BUTTON_COLOR;
        Gui.drawRect(plusBtnX, btnRowTop, plusBtnX + 10, btnRowBottom, plusColor);
        drawCenteredString(fr, "+", plusBtnX + 5, btnYCenter, TEXT_COLOR);

        // === Row 2: 当前模式显示 ===
        int row2Y = barY + BAR_HEIGHT + 2;
        String modeText = StatCollector.translateToLocalFormatted(
            "screen.rtsbuilding.status.mode",
            getModeDisplayName(state.interaction.currentMode));
        String storageStatus = state.storage.linkedStorageCount > 0
            ? StatCollector
                .translateToLocalFormatted("screen.rtsbuilding.status.storage_linked", state.storage.linkedStorageCount)
            : StatCollector.translateToLocal("screen.rtsbuilding.status.storage_not_linked");
        fr.drawString(modeText + "  |  " + storageStatus, 8, row2Y, 0xFFDDDDDD);

        // === Row 3: 状态信息行 ===
        int row3Y = barY + BAR_HEIGHT + ROW2_HEIGHT + 2;
        StringBuilder statusLine = new StringBuilder();

        if (state.interaction.autoStoreMinedDrops) {
            statusLine.append(StatCollector.translateToLocal("screen.rtsbuilding.status.auto_store_on"));
        } else {
            statusLine.append(StatCollector.translateToLocal("screen.rtsbuilding.status.auto_store_off"));
        }

        if (state.interaction.funnelActive) {
            statusLine.append("  ");
            statusLine.append(StatCollector.translateToLocal("screen.rtsbuilding.status.funnel_active"));
        }

        if (state.interaction.quickBuildActive) {
            statusLine.append("  ");
            statusLine.append(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.status.shape",
                    StatCollector.translateToLocal("screen.rtsbuilding.shape." + state.interaction.quickBuildShape)));
            statusLine.append("  ");
            statusLine.append(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.status.fill",
                    StatCollector.translateToLocal("screen.rtsbuilding.fill." + state.interaction.quickBuildFill)));
        }

        if (state.interaction.quickBuildActive && state.interaction.quickBuildRotation != 0) {
            statusLine.append("  ");
            statusLine.append(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.status.rotation",
                    state.interaction.quickBuildRotation * 90));
        }

        if (state.interaction.ultimineActive) {
            statusLine.append("  ");
            statusLine.append(
                StatCollector
                    .translateToLocalFormatted("screen.rtsbuilding.status.ultimine", state.interaction.ultimineLimit));
        }

        if (state.interaction.bindingEditMode) {
            statusLine.append("  ");
            statusLine.append(StatCollector.translateToLocal("screen.rtsbuilding.status.gui_bind_armed"));
        }

        fr.drawString(statusLine.toString(), 8, row3Y, 0xFF999999);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        int btnRowTop = barY + 2;
        int btnRowBottom = barY + BAR_HEIGHT - 2;

        int btnX = 4;
        for (int i = 0; i < TOP_BUTTONS.length; i++) {
            int bw = (i < MODE_BUTTON_COUNT) ? MODE_BUTTON_W : ACTION_BUTTON_W;
            int btnRight = btnX + bw;
            if (mouseX >= btnX && mouseX <= btnRight && mouseY >= btnRowTop && mouseY <= btnRowBottom) {
                handleButtonClick(TOP_BUTTONS[i]);
                return true;
            }
            btnX = btnRight + BUTTON_SPACING;
        }

        int barW = barWidth > 0 ? barWidth : 400;
        String zoomLabel = String.format("%.0f%%", state.uiZoom * 100);
        int zoomLabelWidth = screenMinecraft().fontRenderer.getStringWidth(zoomLabel);
        int zoomBaseX = barW - zoomLabelWidth - 60;
        int minusBtnX = zoomBaseX;
        int plusBtnX = zoomBaseX + zoomLabelWidth + 30;

        if (mouseX >= minusBtnX && mouseX <= minusBtnX + 10 && mouseY >= btnRowTop && mouseY <= btnRowBottom) {
            state.uiZoom = Math.max(ZOOM_MIN, state.uiZoom - ZOOM_STEP);
            return true;
        }
        if (mouseX >= plusBtnX && mouseX <= plusBtnX + 10 && mouseY >= btnRowTop && mouseY <= btnRowBottom) {
            state.uiZoom = Math.min(ZOOM_MAX, state.uiZoom + ZOOM_STEP);
            return true;
        }

        return false;
    }

    private static net.minecraft.client.Minecraft screenMinecraft() {
        return net.minecraft.client.Minecraft.getMinecraft();
    }

    private boolean isButtonActive(BtnId id) {
        switch (id) {
            case INTERACT:
                return state.interaction.currentMode == BuilderMode.INTERACT;
            case LINK_STORAGE:
                return state.interaction.currentMode == BuilderMode.LINK_STORAGE;
            case ROTATE:
                return state.interaction.currentMode == BuilderMode.ROTATE;
            case FUNNEL:
                return state.interaction.currentMode == BuilderMode.FUNNEL;
            case QUICK_BUILD:
                return state.interaction.quickBuildActive;
            case ULTIMINE:
                return state.interaction.ultimineActive;
            case CHUNK_VIEW:
                return state.interaction.chunkViewActive;
            case GUIDE:
                return false;
            case GEAR:
                return isGearPanelOpen();
            default:
                return false;
        }
    }

    private void handleButtonClick(BtnId id) {
        switch (id) {
            case INTERACT:
                state.interaction.currentMode = BuilderMode.INTERACT;
                break;
            case LINK_STORAGE:
                state.interaction.currentMode = BuilderMode.LINK_STORAGE;
                break;
            case ROTATE:
                state.interaction.currentMode = BuilderMode.ROTATE;
                break;
            case FUNNEL:
                state.interaction.currentMode = BuilderMode.FUNNEL;
                state.interaction.funnelActive = true;
                break;
            case QUICK_BUILD:
                state.interaction.quickBuildActive = !state.interaction.quickBuildActive;
                break;
            case ULTIMINE:
                state.interaction.ultimineActive = !state.interaction.ultimineActive;
                break;
            case CHUNK_VIEW:
                state.interaction.chunkViewActive = !state.interaction.chunkViewActive;
                break;
            case GUIDE:
                break;
            case GEAR:
                toggleGearPanel();
                break;
        }
    }

    private void toggleGearPanel() {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
            ((com.rtsbuilding.rtsbuilding.client.RtsScreen) current).getGearMenuPanel()
                .toggle();
        }
    }

    private boolean isGearPanelOpen() {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
            return ((com.rtsbuilding.rtsbuilding.client.RtsScreen) current).getGearMenuPanel()
                .isOpen();
        }
        return false;
    }

    private static String getButtonIcon(BtnId id) {
        switch (id) {
            case QUICK_BUILD:
                return ICON_QUICK_BUILD;
            case ULTIMINE:
                return ICON_ULTIMINE;
            case CHUNK_VIEW:
                return ICON_CHUNK_VIEW;
            case GUIDE:
                return ICON_GUIDE;
            case GEAR:
                return ICON_GEAR;
            default:
                return "?";
        }
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
        hoveredButton = -1;
    }

    private static void drawCenteredString(FontRenderer fr, String text, int x, int y, int color) {
        fr.drawString(text, x - fr.getStringWidth(text) / 2, y, color);
    }

    private static String getModeDisplayName(BuilderMode mode) {
        switch (mode) {
            case INTERACT:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.interact");
            case LINK_STORAGE:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.link_storage");
            case ROTATE:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.rotate");
            case FUNNEL:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.funnel");
            case OFF:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.idle");
            case SELECT_PAN:
                return StatCollector.translateToLocal("screen.rtsbuilding.mode.camera");
            default:
                return mode.name();
        }
    }
}
