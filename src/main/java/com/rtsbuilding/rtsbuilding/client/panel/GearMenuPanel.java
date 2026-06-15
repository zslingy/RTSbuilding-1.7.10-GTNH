package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.CameraViewModel;
import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

/**
 * GearMenuPanel — RTS 控制设置浮动窗口面板。
 *
 * 对标原版 GearMenuPanel (355行)，在 RtsScreen 内渲染，不切屏。
 * 包含 15 个控制项：灵敏度滑块、UI缩放、自动入库、镜头起始位置、
 * 放置回收、Debug按钮、容器Overlay、Shift存入、水平/垂直拖屏反向、
 * 平滑镜头、受击音效、半血退出、BD网络、线框预览。
 */
public class GearMenuPanel implements IRtsPanel {

    private static final String PANEL_NAME = "gear_menu";
    static final int DEFAULT_W = 300;
    static final int MIN_W = 240;
    static final int DEFAULT_H = 284;
    static final int MIN_H = 168;
    private static final int TITLE_BAR_H = 16;
    private static final int CONTENT_TOP = 8;
    static final int CONTENT_H = 580;

    private final RtsClientState state;
    private boolean open = false;
    private int winX, winY, winW, winH;
    private int scroll = 0;
    private boolean dragging = false;
    private int dragOffX, dragOffY;

    public GearMenuPanel() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(winX, winY, winW, winH);
    }

    @Override
    public boolean isVisible() {
        return open;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        this.scroll = 0;
        this.open = true;
    }

    public void close() {
        this.open = false;
        this.state.settingsScreenOpen = false;
    }

    public void toggle() {
        if (open) close();
        else open();
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!open) return;

        FontRenderer fr = screen.mc.fontRenderer;

        // 初始化位置
        if (winW == 0) {
            winW = Math.min(DEFAULT_W, screen.width - 16);
            winH = DEFAULT_H;
            winX = Math.max(8, (screen.width - winW) / 2);
            winY = Math.max(60, (screen.height - winH) / 2);
        }

        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        // 窗口背景
        Gui.drawRect(winX, winY, winX + winW, winY + winH, 0xDD0D1118);
        // 标题栏
        Gui.drawRect(winX + 1, winY + 1, winX + winW - 1, winY + TITLE_BAR_H + 1, 0xDD1C2430);
        // 边框
        Gui.drawRect(winX, winY, winX + winW, winY + 1, 0xFF647088);
        Gui.drawRect(winX, winY + winH - 1, winX + winW, winY + winH, 0xFF0A0C12);
        Gui.drawRect(winX, winY, winX + 1, winY + winH, 0xFF647088);
        Gui.drawRect(winX + winW - 1, winY, winX + winW, winY + winH, 0xFF0A0C12);

        // 标题
        String title = StatCollector.translateToLocal("screen.rtsbuilding.settings.title");
        fr.drawString(title, winX + 8, winY + 4, 0xFFF4F7FF);

        // 关闭按钮 X
        int closeX = winX + winW - 18;
        int closeColor = (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= winY + 2 && mouseY <= winY + 14)
            ? 0xFFFF5555
            : 0xFF8890A0;
        fr.drawString("\u2715", closeX, winY + 2, closeColor);

        // 内容区域（P2-6: 添加Scissor裁剪防止内容溢出）
        int cx = winX + 8;
        int cy = winY + TITLE_BAR_H + CONTENT_TOP - scroll;
        int cw = winW - 16;

        // P2-6: 使用GL11.glScissor裁剪内容区域，防止子面板滑动超过父面板
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // glScissor使用屏幕坐标（Y轴向上），需要转换
        Minecraft mc = Minecraft.getMinecraft();
        int scissorX = (winX + 2) * mc.displayWidth / screen.width;
        int scissorY = mc.displayHeight - (winY + winH - 2) * mc.displayHeight / screen.height;
        int scissorW = (winW - 4) * mc.displayWidth / screen.width;
        int scissorH = (winH - TITLE_BAR_H - 4) * mc.displayHeight / screen.height;
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        renderControls(fr, mouseX, mouseY + scroll, cx, cy, cw);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // 滚动条
        renderScrollbar(winX + winW - 8, winY + TITLE_BAR_H, winH - TITLE_BAR_H);
    }

    private void renderControls(FontRenderer fr, int mouseX, int contentMouseY, int x, int y, int w) {
        InteractionViewModel ivm = state.interaction;
        CameraViewModel cam = state.camera;

        // 标题分隔
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.controls"),
            x + 2,
            y,
            0xFFF4F7FF,
            false);
        drawPanelFrame(x, y + 12, w, CONTENT_H - 12);

        // ── 灵敏度 ──
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.sensitivity"),
            x + 8,
            y + 22,
            0xFFC8D3DF,
            false);
        String sensLabel = String.format("%.0f%%", cam.sensitivity * 100);
        int sensLabelW = fr.getStringWidth(sensLabel);
        fr.drawString(sensLabel, x + w - 16 - sensLabelW, y + 22, 0xFFEAF4FF, false);

        // 滑条
        int trackX = x + 8;
        int trackY = y + 40;
        int trackW = w - 16;
        Gui.drawRect(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        Gui.drawRect(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
        int presetCount = CameraViewModel.INPUT_SENSITIVITY_PRESETS.length;
        int knobX = trackX
            + (int) Math.round((cam.inputSensitivityIndex / (double) Math.max(1, presetCount - 1)) * trackW);
        Gui.drawRect(knobX - 3, trackY - 5, knobX + 4, trackY + 8, 0xFF5FE36C);
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.slow"),
            trackX,
            trackY + 10,
            0xFFB5C1CE,
            false);
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.fast"),
            trackX + trackW - fr.getStringWidth("Fast") - 4,
            trackY + 10,
            0xFFB5C1CE,
            false);

        // ── UI缩放 ──
        int scaleY = y + 68;
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.ui_scale"),
            x + 8,
            scaleY + 7,
            0xFFC8D3DF,
            false);
        drawButton(fr, mouseX, contentMouseY, minusX, scaleY, 22, 22, "-", false);
        Gui.drawRect(valueX, scaleY, valueX + 56, scaleY + 22, 0xCC1A232E);
        String scaleLabel = String.format("%.1fx", state.uiZoom);
        fr.drawString(scaleLabel, valueX + 28 - fr.getStringWidth(scaleLabel) / 2, scaleY + 7, 0xFFEAF4FF, false);
        drawButton(fr, mouseX, contentMouseY, plusX, scaleY, 22, 22, "+", false);

        // ── 自动入库 ──
        int autoStoreY = y + 110;
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.auto_store"),
            x + 8,
            autoStoreY + 7,
            0xFFC8D3DF,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, autoStoreY, 76, 22, ivm.autoStoreMinedDrops);

        // ── 镜头从玩家头顶开始 ──
        int headStartY = autoStoreY + 28;
        fr.drawString(
            StatCollector.translateToLocal("screen.rtsbuilding.settings.head_start"),
            x + 8,
            headStartY + 7,
            0xFFC8D3DF,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, headStartY, 76, 22, ivm.startCameraAtPlayerHead);

        // ── 回收RTS放置方块 ──
        int placedRecoveryY = headStartY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.placed_recovery"),
                w - 116),
            x + 8,
            placedRecoveryY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.placed_recovery.hint"),
                w - 116),
            x + 8,
            placedRecoveryY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(
            fr,
            mouseX,
            contentMouseY,
            x + w - 92,
            placedRecoveryY + 4,
            76,
            22,
            ivm.allowPlacedBlockRecovery);

        // ── Debug按钮 ──
        int debugY = placedRecoveryY + 36;
        fr.drawString(
            fr.trimStringToWidth(StatCollector.translateToLocal("screen.rtsbuilding.settings.debug_button"), w - 116),
            x + 8,
            debugY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.debug_button.hint"),
                w - 116),
            x + 8,
            debugY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, debugY + 4, 76, 22, ivm.debugButtonVisible);

        // ── 容器Overlay ──
        int overlayY = debugY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.container_overlay"),
                w - 116),
            x + 8,
            overlayY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.container_overlay.hint"),
                w - 116),
            x + 8,
            overlayY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, overlayY + 4, 76, 22, ivm.containerOverlayEnabled);

        // ── Shift存入储存 ──
        int shiftY = overlayY + 36;
        fr.drawString(
            fr.trimStringToWidth(StatCollector.translateToLocal("screen.rtsbuilding.settings.shift_import"), w - 116),
            x + 8,
            shiftY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.shift_import.hint"),
                w - 116),
            x + 8,
            shiftY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, shiftY + 4, 76, 22, ivm.shiftImportEnabled);

        // ── 水平拖屏反向 ──
        int panXInvertY = shiftY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.pan_drag_x_invert"),
                w - 116),
            x + 8,
            panXInvertY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.pan_drag_x_invert.hint"),
                w - 116),
            x + 8,
            panXInvertY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, panXInvertY + 4, 76, 22, ivm.invertPanDragX);

        // ── 垂直拖屏反向 ──
        int panYInvertY = panXInvertY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.pan_drag_y_invert"),
                w - 116),
            x + 8,
            panYInvertY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.pan_drag_y_invert.hint"),
                w - 116),
            x + 8,
            panYInvertY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, panYInvertY + 4, 76, 22, ivm.invertPanDragY);

        // ── 平滑镜头 ──
        int smoothY = panYInvertY + 36;
        fr.drawString(
            fr.trimStringToWidth(StatCollector.translateToLocal("screen.rtsbuilding.settings.smooth_camera"), w - 116),
            x + 8,
            smoothY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.smooth_camera.hint"),
                w - 116),
            x + 8,
            smoothY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, smoothY + 4, 76, 22, ivm.smoothCamera);

        // ── 受击音效 ──
        int damageSoundY = smoothY + 36;
        fr.drawString(
            fr.trimStringToWidth(StatCollector.translateToLocal("screen.rtsbuilding.settings.damage_sound"), w - 116),
            x + 8,
            damageSoundY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.damage_sound.hint"),
                w - 116),
            x + 8,
            damageSoundY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, damageSoundY + 4, 76, 22, ivm.damageSoundEnabled);

        // ── 半血自动退出RTS ──
        int autoReturnY = damageSoundY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.damage_auto_return"),
                w - 116),
            x + 8,
            autoReturnY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.damage_auto_return.hint"),
                w - 116),
            x + 8,
            autoReturnY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, autoReturnY + 4, 76, 22, ivm.damageAutoReturnEnabled);

        // ── BD网络 ──
        int bdY = autoReturnY + 36;
        fr.drawString(
            fr.trimStringToWidth(StatCollector.translateToLocal("screen.rtsbuilding.settings.bd_network"), w - 116),
            x + 8,
            bdY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.bd_network.hint"),
                w - 116),
            x + 8,
            bdY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(fr, mouseX, contentMouseY, x + w - 92, bdY + 4, 76, 22, ivm.bdNetworkEnabled);

        // ── 线框预览 ──
        int wireframeY = bdY + 36;
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.wireframe_preview"),
                w - 116),
            x + 8,
            wireframeY + 2,
            0xFFC8D3DF,
            false);
        fr.drawString(
            fr.trimStringToWidth(
                StatCollector.translateToLocal("screen.rtsbuilding.settings.wireframe_preview.hint"),
                w - 116),
            x + 8,
            wireframeY + 13,
            0xFF9FB0C2,
            false);
        drawToggleButton(
            fr,
            mouseX,
            contentMouseY,
            x + w - 92,
            wireframeY + 4,
            76,
            22,
            Config.isWireframePreviewEnabled());
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!open) return false;

        // 关闭按钮
        int closeX = winX + winW - 18;
        if (button == 0 && mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= winY + 2 && mouseY <= winY + 14) {
            close();
            return true;
        }

        // 标题栏拖拽
        if (button == 0 && mouseY >= winY
            && mouseY <= winY + TITLE_BAR_H
            && mouseX >= winX
            && mouseX <= winX + winW - 20) {
            dragging = true;
            dragOffX = mouseX - winX;
            dragOffY = mouseY - winY;
            return true;
        }

        // 内容区域点击
        int contentMouseY = mouseY + scroll;
        int cx = winX + 8;
        int cw = winW - 16;
        int y = winY + TITLE_BAR_H + CONTENT_TOP;

        handleContentClick(mouseX, contentMouseY, cx, y, cw);
        return true;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scrollAmt) {
        if (!open) return false;
        int max = maxScroll();
        if (max <= 0) return true;
        scroll = Math.max(0, Math.min(scroll + (scrollAmt > 0 ? -18 : 18), max));
        return true;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        return false;
    }

    @Override
    public void resetFrameState() {
        if (!open) return;
    }

    // 由 RtsScreen.handleMouseInput 在每帧处理拖拽
    public void handleDragUpdate(int mouseX, int mouseY) {
        if (dragging) {
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen screen = mc.currentScreen;
            if (screen != null) {
                winX = Math.max(0, Math.min(mouseX - dragOffX, screen.width - winW));
                winY = Math.max(0, Math.min(mouseY - dragOffY, screen.height - winH));
            }
        }
    }

    public void stopDrag() {
        dragging = false;
    }

    // ======== 内容点击处理 ========

    private void handleContentClick(int mouseX, int contentMouseY, int x, int y, int w) {
        InteractionViewModel ivm = state.interaction;
        CameraViewModel cam = state.camera;

        // 灵敏度滑条
        if (inside(mouseX, contentMouseY, x + 8, y + 32, w - 16, 24)) {
            int trackX = x + 8;
            int trackW = w - 16;
            int presetCount = CameraViewModel.INPUT_SENSITIVITY_PRESETS.length;
            double frac = (mouseX - trackX) / (double) Math.max(1, trackW);
            int idx = (int) Math.round(frac * (presetCount - 1));
            idx = Math.max(0, Math.min(idx, presetCount - 1));
            cam.inputSensitivityIndex = idx;
            cam.sensitivity = CameraViewModel.INPUT_SENSITIVITY_PRESETS[idx];
            return;
        }

        // UI缩放 -
        if (inside(mouseX, contentMouseY, x + w - 124, y + 68, 22, 22)) {
            state.uiZoom = Math.max(0.5f, state.uiZoom - 0.5f);
            return;
        }
        // UI缩放 +
        if (inside(mouseX, contentMouseY, x + w - 124 + 86, y + 68, 22, 22)) {
            state.uiZoom = Math.min(4.0f, state.uiZoom + 0.5f);
            return;
        }

        // 自动入库
        if (inside(mouseX, contentMouseY, x + 8, y + 106, w - 16, 28)) {
            ivm.autoStoreMinedDrops = !ivm.autoStoreMinedDrops;
            return;
        }
        // 镜头从玩家头顶开始
        if (inside(mouseX, contentMouseY, x + 8, y + 134, w - 16, 28)) {
            ivm.startCameraAtPlayerHead = !ivm.startCameraAtPlayerHead;
            return;
        }
        // 放置回收
        if (inside(mouseX, contentMouseY, x + 8, y + 166, w - 16, 34)) {
            ivm.allowPlacedBlockRecovery = !ivm.allowPlacedBlockRecovery;
            return;
        }
        // Debug按钮
        if (inside(mouseX, contentMouseY, x + 8, y + 202, w - 16, 34)) {
            ivm.debugButtonVisible = !ivm.debugButtonVisible;
            return;
        }
        // 容器Overlay
        if (inside(mouseX, contentMouseY, x + 8, y + 238, w - 16, 34)) {
            ivm.containerOverlayEnabled = !ivm.containerOverlayEnabled;
            return;
        }
        // Shift存入
        if (inside(mouseX, contentMouseY, x + 8, y + 274, w - 16, 34)) {
            ivm.shiftImportEnabled = !ivm.shiftImportEnabled;
            return;
        }
        // 水平拖屏反向
        if (inside(mouseX, contentMouseY, x + 8, y + 310, w - 16, 34)) {
            ivm.invertPanDragX = !ivm.invertPanDragX;
            return;
        }
        // 垂直拖屏反向
        if (inside(mouseX, contentMouseY, x + 8, y + 346, w - 16, 34)) {
            ivm.invertPanDragY = !ivm.invertPanDragY;
            return;
        }
        // 平滑镜头
        if (inside(mouseX, contentMouseY, x + 8, y + 382, w - 16, 34)) {
            ivm.smoothCamera = !ivm.smoothCamera;
            return;
        }
        // 受击音效
        if (inside(mouseX, contentMouseY, x + 8, y + 418, w - 16, 34)) {
            ivm.damageSoundEnabled = !ivm.damageSoundEnabled;
            return;
        }
        // 半血自动退出
        if (inside(mouseX, contentMouseY, x + 8, y + 454, w - 16, 34)) {
            ivm.damageAutoReturnEnabled = !ivm.damageAutoReturnEnabled;
            return;
        }
        // BD网络
        if (inside(mouseX, contentMouseY, x + 8, y + 490, w - 16, 34)) {
            ivm.bdNetworkEnabled = !ivm.bdNetworkEnabled;
            return;
        }
        // 线框预览
        if (inside(mouseX, contentMouseY, x + 8, y + 526, w - 16, 34)) {
            Config.setWireframePreviewEnabled(!Config.isWireframePreviewEnabled());
        }
    }

    // ======== 辅助渲染方法 ========

    private void drawPanelFrame(int x, int y, int w, int h) {
        Gui.drawRect(x, y, x + w, y + h, 0xDD111720);
        Gui.drawRect(x, y, x + w, y + 1, 0xFF384351);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF080B10);
        Gui.drawRect(x, y, x + 1, y + h, 0xFF384351);
        Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFF080B10);
    }

    private void drawButton(FontRenderer fr, int mouseX, int mouseY, int x, int y, int w, int h, String label,
        boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xCC2D7C4B : (hover ? 0xCC334054 : 0xCC26303D);
        Gui.drawRect(x, y, x + w, y + h, bg);
        Gui.drawRect(x, y, x + w, y + 1, 0xFF6A8299);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF0E1116);
        fr.drawString(label, x + w / 2 - fr.getStringWidth(label) / 2, y + 7, 0xFFF2F6FB, false);
    }

    private void drawToggleButton(FontRenderer fr, int mouseX, int mouseY, int x, int y, int w, int h, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? (hover ? 0xDD45BA53 : 0xDD329A42) : (hover ? 0xDD3D4957 : 0xDD28313C);
        Gui.drawRect(x, y, x + w, y + h, bg);
        Gui.drawRect(x, y, x + w, y + 1, active ? 0xFF8EF19A : 0xFF68788A);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF10151B);

        String label;
        if (active) {
            label = StatCollector.translateToLocal("gui.rtsbuilding.on");
        } else {
            label = StatCollector.translateToLocal("gui.rtsbuilding.off");
        }
        int labelX = active ? x + w - 26 : x + 6;
        Gui.drawRect(labelX, y + 4, labelX + 18, y + h - 4, active ? 0xFF72F07A : 0xFF788696);
        fr.drawString(label, x + w / 2 - fr.getStringWidth(label) / 2, y + 7, 0xFFF7FBFF, false);
    }

    private void renderScrollbar(int barX, int barY, int barH) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        Gui.drawRect(barX, barY + 2, barX + 2, barY + barH - 2, 0x88313A46);
        double thumbH = Math.max(18, barH * (barH / (double) Math.max(barH, CONTENT_H + CONTENT_TOP)));
        int thumbY = barY + (int) Math.round((barH - thumbH) * (scroll / (double) maxScroll));
        Gui.drawRect(barX - 1, thumbY, barX + 3, thumbY + (int) thumbH, 0xCC8AA0B8);
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_H + CONTENT_TOP - (winH - TITLE_BAR_H));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
