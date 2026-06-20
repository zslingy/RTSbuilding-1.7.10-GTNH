package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;

/**
 * 顶部工具栏面板 — 模式按钮、操作按钮、状态栏。
 *
 * 重构版本：对齐 RTSbuilding-main 的 TopBarPanel：
 * - 动态按钮布局（每帧构建按钮列表）
 * - 2行紧凑状态栏（对齐原版 52px 总高度）
 * - 蓝图放置锁定时模式按钮统一高亮 INTERACT
 */
public class RtsTopBarPanel implements IRtsPanel {

    private static final String PANEL_NAME = "top_bar";

    // 布局常量（对齐 BuilderScreenConstants.TOP_H = 52）
    static final int BUTTON_ROW_H = 24;
    static final int STATUS_ROW_H = 14;
    static final int TOTAL_HEIGHT = BUTTON_ROW_H + STATUS_ROW_H * 2;
    static final int MODE_BUTTON_W = 32;
    static final int ACTION_BUTTON_W = 32;
    static final int ICON_SIZE = 24;
    static final int BUTTON_GAP = 5;
    static final int BAR_BG = 0xCC1F2329;

    // 按钮标识
    private enum BtnId {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE,
        QUICK_BUILD,
        ULTIMINE,
        CHUNK_VIEW,
        GUIDE,
        GEAR
    }

    /** 按钮布局（每帧重建） */
    private static final class ButtonLayout {

        final BtnId id;
        final int x, width;
        final boolean active;

        ButtonLayout(BtnId id, int x, int width, boolean active) {
            this.id = id;
            this.x = x;
            this.width = width;
            this.active = active;
        }
    }

    private final RtsClientState state;
    private int barX, barY, barWidth;
    private int hoveredButton = -1;

    public RtsTopBarPanel() {
        this.state = RtsClientState.get();
    }

    // ======== IRtsPanel 协议 ========

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
        Gui.drawRect(barX, barY, barX + barWidth, barY + TOTAL_HEIGHT, BAR_BG);

        // === 按钮行 ===
        List<ButtonLayout> layouts = buildButtonLayouts(w);
        for (int i = 0; i < layouts.size(); i++) {
            ButtonLayout btn = layouts.get(i);
            renderTopBarButton(screen, btn, mouseX, mouseY);
            if (isInside(mouseX, mouseY, btn.x, barY + 2, btn.width, BUTTON_ROW_H - 4)) {
                hoveredButton = i;
            }
        }

        // === 状态行 Row 1: 模式 + 存储链接 ===
        int row1Y = barY + BUTTON_ROW_H + 2;
        String modeText = StatCollector.translateToLocalFormatted(
            "screen.rtsbuilding.status.mode",
            getModeDisplayName(state.interaction.currentMode));
        String linked = state.storage.linkedStorageCount > 0
            ? StatCollector
                .translateToLocalFormatted("screen.rtsbuilding.status.storage_linked", state.storage.linkedStorageCount)
            : StatCollector.translateToLocal("screen.rtsbuilding.status.storage_not_linked");
        String row1 = modeText;
        fr.drawString(fr.trimStringToWidth(row1, w - 16), 8, row1Y, 0xFFF0F0F0);

        // === 状态行 Row 2: 详细状态 ===
        int row2Y = barY + BUTTON_ROW_H + STATUS_ROW_H + 2;
        StringBuilder sb = new StringBuilder();
        sb.append(linked)
            .append("  ");

        sb.append(
            state.interaction.autoStoreMinedDrops
                ? StatCollector.translateToLocal("screen.rtsbuilding.status.auto_store_on")
                : StatCollector.translateToLocal("screen.rtsbuilding.status.auto_store_off"));
        sb.append("  ");

        sb.append(
            StatCollector.translateToLocalFormatted(
                "screen.rtsbuilding.status.funnel",
                state.interaction.funnelActive ? StatCollector.translateToLocal("gui.rtsbuilding.on")
                    : StatCollector.translateToLocal("gui.rtsbuilding.off")));

        if (state.interaction.quickBuildActive) {
            sb.append("  ");
            sb.append(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.status.shape",
                    StatCollector.translateToLocal("screen.rtsbuilding.shape." + state.interaction.quickBuildShape)));
            sb.append("  ");
            sb.append(
                StatCollector.translateToLocalFormatted(
                    "screen.rtsbuilding.status.fill",
                    StatCollector.translateToLocal("screen.rtsbuilding.fill." + state.interaction.quickBuildFill)));
            if (state.interaction.quickBuildRotation != 0) {
                sb.append("  ");
                sb.append(
                    StatCollector.translateToLocalFormatted(
                        "screen.rtsbuilding.status.rotation",
                        state.interaction.quickBuildRotation * 15));
            }
        }

        if (state.interaction.ultimineActive) {
            sb.append("  ");
            sb.append(
                StatCollector
                    .translateToLocalFormatted("screen.rtsbuilding.status.ultimine", state.interaction.ultimineLimit));
        }

        if (state.interaction.bindingEditMode) {
            sb.append("  ");
            sb.append(StatCollector.translateToLocal("screen.rtsbuilding.status.gui_bind_armed"));
        }

        int row2Color = state.storage.linkedStorageCount > 0 ? 0xFFB8FFB8 : 0xFFFFD8AE;
        fr.drawString(fr.trimStringToWidth(sb.toString(), w - 16), 8, row2Y, row2Color);
    }

    // ======== 按钮布局构建 ========

    private List<ButtonLayout> buildButtonLayouts(int screenW) {
        List<ButtonLayout> list = new ArrayList<>();
        int x = 8;

        // 模式按钮组（左侧）
        list.add(
            new ButtonLayout(BtnId.INTERACT, x, MODE_BUTTON_W, state.interaction.currentMode == BuilderMode.INTERACT));
        x += MODE_BUTTON_W + BUTTON_GAP;
        list.add(
            new ButtonLayout(BtnId.LINK, x, MODE_BUTTON_W, state.interaction.currentMode == BuilderMode.LINK_STORAGE));
        x += MODE_BUTTON_W + BUTTON_GAP;
        list.add(new ButtonLayout(BtnId.FUNNEL, x, MODE_BUTTON_W, state.interaction.currentMode == BuilderMode.FUNNEL));
        x += MODE_BUTTON_W + BUTTON_GAP;
        list.add(new ButtonLayout(BtnId.ROTATE, x, MODE_BUTTON_W, state.interaction.currentMode == BuilderMode.ROTATE));
        x += MODE_BUTTON_W + BUTTON_GAP + 8;

        // 操作按钮组（中部）
        list.add(new ButtonLayout(BtnId.QUICK_BUILD, x, ACTION_BUTTON_W, state.interaction.quickBuildActive));
        x += ACTION_BUTTON_W + BUTTON_GAP;
        list.add(new ButtonLayout(BtnId.ULTIMINE, x, ACTION_BUTTON_W, state.interaction.ultimineActive));
        x += ACTION_BUTTON_W + BUTTON_GAP;
        list.add(new ButtonLayout(BtnId.CHUNK_VIEW, x, ACTION_BUTTON_W, state.interaction.chunkViewActive));
        x += ACTION_BUTTON_W + BUTTON_GAP;
        list.add(new ButtonLayout(BtnId.GUIDE, x, ACTION_BUTTON_W, false));

        // 齿轮按钮（右对齐）
        int gearX = Math.max(x + BUTTON_GAP, screenW - ACTION_BUTTON_W - 8);
        boolean gearOpen = isGearPanelOpen();
        list.add(new ButtonLayout(BtnId.GEAR, gearX, ACTION_BUTTON_W, gearOpen));

        return list;
    }

    // ======== 按钮渲染 ========

    private void renderTopBarButton(GuiScreen screen, ButtonLayout btn, int mouseX, int mouseY) {
        int x = btn.x;
        int y = barY + 2;
        int w = btn.width;
        int h = BUTTON_ROW_H - 4;

        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        boolean pressed = hover && Mouse.isButtonDown(0);

        // 背景色
        int bg = 0xAA1F2329;
        int light = 0xFF5B6673;
        int dark = 0xFF0D0E10;
        if (btn.active) {
            bg = 0xFF2D6B47;
            light = 0xFF9AD2AE;
        } else if (pressed) {
            bg = 0xFF1F5037;
            light = 0xFF6AA784;
        } else if (hover) {
            bg = 0xFF1D2530;
            light = 0xFF7A90AA;
        }

        Gui.drawRect(x, y, x + w, y + h, bg);
        Gui.drawRect(x, y, x + w, y + 1, light);
        Gui.drawRect(x, y + h, x + w, y + h, dark);
        Gui.drawRect(x, y, x + 1, y + h, light);
        Gui.drawRect(x + w, y, x + w, y + h, dark);

        if (pressed) {
            Gui.drawRect(x + 1, y + 1, x + w - 1, y + 2, dark);
            Gui.drawRect(x + 1, y + 1, x + 2, y + h - 1, dark);
        }

        // 纹理图标
        ResourceLocation texture = getButtonTexture(btn.id, btn.active, hover, pressed);
        int iconX = x + (w - ICON_SIZE) / 2;
        int iconY = y + (h - ICON_SIZE) / 2;
        WindowButton.drawTexture(screen.mc, texture, iconX, iconY, ICON_SIZE, ICON_SIZE);
    }

    // ======== 点击处理 ========

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (hoveredButton < 0) return false;

        List<ButtonLayout> layouts = buildButtonLayouts(barWidth);
        if (hoveredButton >= layouts.size()) return false;

        ButtonLayout btn = layouts.get(hoveredButton);
        handleButtonClick(btn.id);
        return true;
    }

    private boolean isButtonActive(BtnId id) {
        switch (id) {
            case INTERACT:
                return state.interaction.currentMode == BuilderMode.INTERACT;
            case LINK:
                return state.interaction.currentMode == BuilderMode.LINK_STORAGE;
            case FUNNEL:
                return state.interaction.currentMode == BuilderMode.FUNNEL;
            case ROTATE:
                return state.interaction.currentMode == BuilderMode.ROTATE;
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
        // 切换模式时先重置漏斗
        if (id != BtnId.FUNNEL && state.interaction.funnelActive) {
            state.interaction.funnelActive = false;
            state.interaction.funnelHasTarget = false;
            GuiScreen scr = Minecraft.getMinecraft().currentScreen;
            if (scr instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
                ((com.rtsbuilding.rtsbuilding.client.RtsScreen) scr).getFunnelPanel()
                    .setOpen(false);
            }
        }

        switch (id) {
            case INTERACT:
                state.interaction.currentMode = BuilderMode.INTERACT;
                break;
            case LINK:
                state.interaction.currentMode = BuilderMode.LINK_STORAGE;
                break;
            case FUNNEL:
                state.interaction.currentMode = BuilderMode.FUNNEL;
                state.interaction.funnelActive = true;
                GuiScreen funnelScr = Minecraft.getMinecraft().currentScreen;
                if (funnelScr instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
                    ((com.rtsbuilding.rtsbuilding.client.RtsScreen) funnelScr).getFunnelPanel()
                        .setOpen(true);
                }
                break;
            case ROTATE:
                state.interaction.currentMode = BuilderMode.ROTATE;
                break;
            case QUICK_BUILD:
                state.interaction.quickBuildActive = !state.interaction.quickBuildActive;
                if (state.interaction.quickBuildActive) {
                    GuiScreen current = Minecraft.getMinecraft().currentScreen;
                    if (current instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
                        ((com.rtsbuilding.rtsbuilding.client.RtsScreen) current).getQuickBuildPanel()
                            .setOpen(true);
                    }
                }
                break;
            case ULTIMINE:
                state.interaction.ultimineActive = !state.interaction.ultimineActive;
                if (state.interaction.ultimineActive) {
                    GuiScreen current = Minecraft.getMinecraft().currentScreen;
                    if (current instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
                        ((com.rtsbuilding.rtsbuilding.client.RtsScreen) current).getUltiminePanel()
                            .setOpen(true);
                    }
                }
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

    // ======== 其他 IRtsPanel 方法 ========

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

    // ======== 辅助 ========

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static ResourceLocation getButtonTexture(BtnId id, boolean active, boolean hover, boolean pressed) {
        String state = pressed ? "pressed" : active ? "active" : hover ? "hover" : "inactive";
        return new ResourceLocation("rtsbuilding", "textures/gui/topbar/" + getTextureBase(id) + "_" + state + ".png");
    }

    private static String getTextureBase(BtnId id) {
        switch (id) {
            case INTERACT:
                return "mode_interact";
            case LINK:
                return "mode_link";
            case ROTATE:
                return "mode_rotate";
            case FUNNEL:
                return "mode_funnel";
            case QUICK_BUILD:
                return "quick_build";
            case ULTIMINE:
                return "ultimine";
            case CHUNK_VIEW:
                return "chunk_view";
            case GUIDE:
                return "quest_detect";
            case GEAR:
                return "settings_gear";
            default:
                return "mode_interact";
        }
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
