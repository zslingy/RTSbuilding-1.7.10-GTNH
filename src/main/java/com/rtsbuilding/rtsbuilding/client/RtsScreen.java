package com.rtsbuilding.rtsbuilding.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.overlay.DamageFlashOverlay;
import com.rtsbuilding.rtsbuilding.client.panel.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.panel.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsTopBarPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.panel.funnel.FunnelPanel;
import com.rtsbuilding.rtsbuilding.client.panel.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.panel.storage.RecentGridView;
import com.rtsbuilding.rtsbuilding.client.panel.storage.StorageCategoryView;
import com.rtsbuilding.rtsbuilding.client.panel.storage.StorageGridView;
import com.rtsbuilding.rtsbuilding.client.panel.ultimine.UltiminePanel;
import com.rtsbuilding.rtsbuilding.client.panel.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.popup.CraftFeedbackPopup;
import com.rtsbuilding.rtsbuilding.client.popup.CraftQuantityDialog;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesMessage;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStateMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;

/**
 * RTS 主屏幕容器 — 替代原 BuilderScreen (2426 行)。
 * 
 * 阶段A更新：添加相机 tick 逻辑（EMA平滑、心跳、鼠标拖拽）。
 * Bug1修复：添加世界交互穿透（RtsInteractionHandler）。
 * Bug2修复：添加本地预测调用（applyLocalPrediction）。
 * Bug3修复：ESC 关闭时使用 onGuiClosed 清理，避免与 RtsKeyHandler 竞态。
 * 
 * Bug1+Bug2+Bug3 修复 (2026-06-11)：
 * - 移动方向公式对齐原版
 * - 旋转本地预测 (applyFullLocalPrediction)
 * - 本地预测调用顺序修正（先预测再发送）
 * - 拖拽距离追踪（区分点击/拖拽）
 * 
 * 单层容器设计：
 * - 委托输入分发到 RtsInputRouter
 * - 委托状态管理到 RtsClientState（5 个 ViewModel）
 * - 通过 IRtsPanel 协议管理所有面板
 * - CameraInputHelper 处理相机输入管线
 */
public class RtsScreen extends GuiScreen {

    private final RtsClientState state;
    private final RtsInputRouter inputRouter;
    private final Map<String, IRtsPanel> panels;
    private final CameraInputHelper cameraInputHelper;
    private final RtsInteractionHandler interactionHandler;
    private final RtsBottomPanel bottomPanel;
    /** Bug1修复：GearMenuPanel 设置浮动窗口面板 */
    private final GearMenuPanel gearMenuPanel;
    /** Bug2修复：快速建造/连锁挖掘窗口面板 */
    private final QuickBuildPanel quickBuildPanel;
    private final UltiminePanel ultiminePanel;
    private final FunnelPanel funnelPanel;
    private final RtsWorkflowPanel workflowPanel;
    private final RtsResumePlacementPanel resumePanel;
    private final GuidePanel guidePanel;

    /** 上一次请求的页面，用于翻页时重新请求 */
    private int lastRequestedPage = -1;
    /** 是否已发起初始数据请求 */
    private boolean initialDataRequested = false;

    /** 中键拖拽距离累积，用于区分单击和拖拽 */
    private double middleDragDistance = 0.0D;
    /** 中键按下状态 */
    private boolean middleMouseDown = false;
    /** 中键拖拽平移的像素阈值，与右键旋转阈值一致 */
    private static final double MIDDLE_DRAG_THRESHOLD = 1.5D;

    // ---- Bug2修复：左键挖掘追踪 ----
    /** 左键按下状态（用于渐进式挖掘 abort） */
    private boolean leftMouseDown = false;
    /** Bug2修复：记录挖掘启动时刻，防止同帧 start+abort 竞态 */
    private int mineStartedTick = -1;

    /** 世界区域缓存（在 render 中更新） */
    private int worldAreaTop = 0;
    private int worldAreaBottom = 0;

    public RtsScreen() {
        this.state = RtsClientState.get();
        this.inputRouter = new RtsInputRouter();
        this.panels = new LinkedHashMap<>();
        this.cameraInputHelper = new CameraInputHelper();
        this.interactionHandler = new RtsInteractionHandler();
        this.bottomPanel = new RtsBottomPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.quickBuildPanel = new QuickBuildPanel();
        this.ultiminePanel = new UltiminePanel();
        this.funnelPanel = new FunnelPanel();
        this.workflowPanel = new RtsWorkflowPanel();
        this.resumePanel = new RtsResumePlacementPanel();
        this.guidePanel = new GuidePanel();
    }

    @Override
    public void initGui() {
        // 清除旧面板和路由器状态
        inputRouter.clearPanels();
        panels.clear();

        // ---- 顶部工具栏 ----
        addPanel(new RtsTopBarPanel());

        // ---- Bug6修复：创建底部子面板并注入到底部容器，不再独立注册 ----
        StorageGridView storageGrid = new StorageGridView();
        StorageCategoryView categoryPanel = new StorageCategoryView();
        RecentGridView recentGrid = new RecentGridView();
        com.rtsbuilding.rtsbuilding.client.panel.fluid.FluidStripView fluidStrip = new com.rtsbuilding.rtsbuilding.client.panel.fluid.FluidStripView();
        bottomPanel.setSubPanels(storageGrid, categoryPanel, recentGrid, fluidStrip);

        // ---- 底部面板容器（唯一底部容器，管理所有子面板渲染和输入） ----
        addPanel(bottomPanel);

        // ---- 浮动弹窗/叠加层（不重叠于底部区域，保持独立注册） ----
        addPanel(new CraftQuantityDialog());
        addPanel(new CraftFeedbackPopup());

        // ---- Bug2修复: 快速建造+连锁挖掘窗口面板 ----
        addPanel(quickBuildPanel);
        addPanel(ultiminePanel);
        addPanel(funnelPanel);

        // ---- 阶段6: 蓝图面板 ----
        addPanel(new BlueprintPanel());

        // ---- 伤害闪烁叠加层 ----
        addPanel(new DamageFlashOverlay());

        // ---- Bug1修复: GearMenuPanel 设置浮动窗口面板 ----
        addPanel(gearMenuPanel);

        // ---- 阶段5: 发起初始服务端数据请求 ----
        if (!initialDataRequested) {
            requestInitialData();
            initialDataRequested = true;
        }
    }

    /**
     * 向服务端请求存储、合成、进度数据。
     * 阶段5网络联调——这些请求通过 C2S → S2C 往返填充 ViewModel。
     */
    private void requestInitialData() {
        RtsNetworkManager.NETWORK.sendToServer(new C2SRtsRequestStoragePageMessage(0, 0, state.storage.sortMode));
        RtsNetworkManager.NETWORK.sendToServer(new C2SRtsRequestCraftablesMessage("", false, 0, 50, false, null));
        RtsNetworkManager.NETWORK.sendToServer(new C2SRtsRequestProgressionStateMessage());
    }

    /**
     * 翻页时重新请求存储数据。
     */
    public void requestStoragePage(int page) {
        if (page != lastRequestedPage) {
            RtsNetworkManager.NETWORK
                .sendToServer(new C2SRtsRequestStoragePageMessage(page, 0, state.storage.sortMode));
            lastRequestedPage = page;
        }
    }

    @Override
    public void updateScreen() {
        if (mc.currentScreen != this) return;
        if (state.settingsScreenOpen) {
            super.updateScreen();
            return;
        }
        if (!state.camera.isActive) return;
        super.updateScreen();

        // Bug5修复：计算帧时间差（平滑镜头模式下用于帧率无关的移动插值）
        float cameraTickDelta = 1.0f;
        if (state.interaction.smoothCamera) {
            long now = System.nanoTime();
            if (state.camera.lastFrameNanos == 0L) {
                state.camera.lastFrameNanos = now;
            }
            long elapsed = now - state.camera.lastFrameNanos;
            state.camera.lastFrameNanos = now;
            if (elapsed > 0L) {
                cameraTickDelta = Math.max(0.0f, Math.min((float) elapsed / 50_000_000L, 2.0f));
            }
        } else {
            state.camera.lastFrameNanos = 0L;
        }

        // ---- 阶段A：相机 tick 逻辑 ----
        if (state.camera.isActive) {
            // Bug1修复：RTS GUI模式下释放鼠标，允许鼠标指针自由移动和点击UI
            Mouse.setGrabbed(false);
            mc.inGameHasFocus = false;

            // Bug5修复：创建/同步本地镜像相机（纯客户端渲染用）
            ensureLocalMirrorCamera();
            snapLocalMirrorCameraPose();

            // 收集输入并应用 EMA 平滑
            cameraInputHelper.updateInputFromKeyBindings();

            // Bug2b修复：先本地预测，再发送网络消息（平滑模式使用帧时间缩放）
            cameraInputHelper.applyFullLocalPrediction(cameraTickDelta);

            // 发送移动消息（含输入时重置心跳）
            boolean hadInput = cameraInputHelper.sendMoveMessageOnInput();

            // 心跳机制：每 20 ticks 发送一次即使无输入
            if (!hadInput && state.camera.tickHeartbeat()) {
                cameraInputHelper.sendHeartbeat();
            }

            // 快模式检测
            state.camera.fastMode = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        }

        // Bug1修复：漏斗模式下持续 raycast 更新目标位置（对齐原版 ClientRtsController.tick）
        if (state.interaction.currentMode == com.rtsbuilding.rtsbuilding.common.BuilderMode.FUNNEL
            && state.interaction.funnelActive) {
            tickFunnelRaycast();
        }

        // 检测翻页变化，自动请求新页面
        if (state.storage.currentPage != lastRequestedPage) {
            requestStoragePage(state.storage.currentPage);
        }
    }

    // Bug1修复：漏斗模式下每 tick 做射线检测，更新漏斗目标坐标到服务端
    private int funnelCooldownTicks = 0;
    private double lastFunnelTargetX, lastFunnelTargetY, lastFunnelTargetZ;
    private boolean hasLastFunnelTarget = false;

    private void tickFunnelRaycast() {
        if (funnelCooldownTicks > 0) {
            funnelCooldownTicks--;
            return;
        }
        net.minecraft.util.MovingObjectPosition hit = RtsInteractionHandler
            .pickBlockHit(state.camera.cameraEntity != null ? state.camera.cameraEntity : state.camera.localMirror);
        if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) return;

        double tx = hit.blockX + 0.5D;
        double ty = hit.blockY + 0.5D;
        double tz = hit.blockZ + 0.5D;

        // 避免重复发送相同目标
        if (hasLastFunnelTarget && Math.abs(tx - lastFunnelTargetX) < 1.0D
            && Math.abs(ty - lastFunnelTargetY) < 1.0D
            && Math.abs(tz - lastFunnelTargetZ) < 1.0D) {
            return;
        }

        lastFunnelTargetX = tx;
        lastFunnelTargetY = ty;
        lastFunnelTargetZ = tz;
        hasLastFunnelTarget = true;
        funnelCooldownTicks = 2;
        state.interaction.funnelHasTarget = true;
        state.interaction.funnelTargetX = tx;
        state.interaction.funnelTargetY = ty;
        state.interaction.funnelTargetZ = tz;

        RtsNetworkManager.NETWORK.sendToServer(
            new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelMessage(
                0,
                0,
                tx,
                ty,
                tz,
                true,
                state.interaction.funnelRangeSize));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (mc.currentScreen != this) return;
        drawRect(0, 0, this.width, 54, 0xC0101116);

        // 重置帧级状态
        state.resetAllFrameStates();
        inputRouter.resetAllFrames();

        // ---- 计算世界区域（顶部栏下方到底部面板上方） ----
        worldAreaTop = 54;
        worldAreaBottom = Math.max(54, bottomPanel.getPanelY());

        // ---- 阶段6: UI 缩放 ----
        float zoom = state.uiZoom;
        GL11.glPushMatrix();
        GL11.glScalef(zoom, zoom, 1.0f);
        float invZoom = 1.0f / zoom;

        int scaledMouseX = (int) (mouseX * invZoom);
        int scaledMouseY = (int) (mouseY * invZoom);

        // 渲染所有可见面板
        for (IRtsPanel panel : panels.values()) {
            if (panel.isVisible()) {
                panel.render(this, scaledMouseX, scaledMouseY, partialTicks);
            }
        }

        // 第七阶段：渲染工作流面板、恢复放置面板、引导面板
        workflowPanel.draw(mc, width - 190, 56);
        resumePanel.setPosition(width / 2 - 100, height / 2 - 40);
        resumePanel.draw(mc);
        guidePanel.draw(mc, width / 2 - 150, height / 2 - 90);

        GL11.glPopMatrix();
    }

    /**
     * 判断鼠标坐标是否在世界区域内（未被任何 UI 面板覆盖的 3D 视图区域）。
     */
    public boolean isWorldArea(int mouseX, int mouseY) {
        return mouseY >= worldAreaTop && mouseY <= worldAreaBottom;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        float invZoom = 1.0f / state.uiZoom;
        int scaledX = (int) (mouseX * invZoom);
        int scaledY = (int) (mouseY * invZoom);

        // ---- Bug3修复：右键按下不触发世界交互，仅记录状态 ----
        if (button == 1) {
            // 对齐原版 beginRightPress：记录右键状态，不触发世界交互
            cameraInputHelper.beginRightPress(scaledX, scaledY, button);

            // 先尝试面板消费
            if (inputRouter.dispatchClick(scaledX, scaledY, button)) {
                return;
            }

            // 世界交互延迟到 mouseMovedOrUp（释放时判断）
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }

        // ---- 中键处理 ----
        if (button == 2) {
            middleMouseDown = false;
            middleDragDistance = 0.0D;
        }

        // ---- 左键处理 ----
        if (button == 0) {
            leftMouseDown = true;
            mineStartedTick = -1;
            middleMouseDown = false;
            middleDragDistance = 0.0D;
        }

        // 先尝试面板消费
        if (inputRouter.dispatchClick(scaledX, scaledY, button)) {
            return;
        }

        // 第七阶段：委托工作流/恢复放置/引导面板点击
        workflowPanel.mouseClicked(scaledX, scaledY, width - 190, 56);
        resumePanel.mouseClicked(scaledX, scaledY);
        guidePanel.mouseClicked(scaledX, scaledY, width / 2 - 150, height / 2 - 90);

        // 世界交互穿透
        if (isWorldArea(scaledX, scaledY) && state.camera.isActive) {
            int displayMouseX = Mouse.getX();
            int displayMouseY = mc.displayHeight - Mouse.getY() - 1;
            if (interactionHandler.handleWorldClick(button, displayMouseX, displayMouseY)) {
                if (button == 0) {
                    // Bug2修复：仅在 handleWorldClick 成功后记录，使用 tick 计数而非 millis
                    mineStartedTick = state.camera.cameraMoveHeartbeatTicks;
                }
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouseInput() {
        // Bug1修复：RTS GUI模式下释放鼠标，Mouse.getDX()/getDY() 在非grabbed状态下仍然可用
        if (state.camera.isActive) {
            Mouse.setGrabbed(false);
            mc.inGameHasFocus = false;
        }
        super.handleMouseInput();

        // ---- 鼠标拖拽相机旋转/平移 ----
        if (state.camera.isActive) {
            // Bug3修复：右键拖拽 = 旋转（由 CameraInputHelper.handleRightDrag 管理状态）
            if (Mouse.isButtonDown(1) && cameraInputHelper.isRightPressActive()) {
                int dx = Mouse.getDX();
                int dy = Mouse.getDY();
                if (dx != 0 || dy != 0) {
                    float invZoom = 1.0f / state.uiZoom;
                    int scaledMouseX = (int) ((Mouse.getX() * width / mc.displayWidth) * invZoom);
                    int scaledMouseY = (int) ((height - Mouse.getY() * height / mc.displayHeight - 1) * invZoom);
                    cameraInputHelper.handleRightDrag(
                        scaledMouseX,
                        scaledMouseY,
                        1,
                        dx,
                        dy,
                        isWorldArea(scaledMouseX, scaledMouseY));
                }
            }
            // Bug3修复：检测右键释放 → 判断点击/拖拽 → 触发世界交互
            if (!Mouse.isButtonDown(1) && cameraInputHelper.isRightPressActive()) {
                // 右键已释放但状态未清理 → 执行 endRightPress
                float invZoom = 1.0f / state.uiZoom;
                int scaledMouseX = (int) ((Mouse.getX() * width / mc.displayWidth) * invZoom);
                int scaledMouseY = (int) ((height - Mouse.getY() * height / mc.displayHeight - 1) * invZoom);
                if (cameraInputHelper.endRightPress(scaledMouseX, scaledMouseY, 1)) {
                    // 是点击（非拖拽），触发世界交互
                    // Bug2修复：computeCursorRay 需要 display 坐标，而非 GUI 缩放坐标
                    int displayMouseX = Mouse.getX();
                    int displayMouseY = mc.displayHeight - Mouse.getY() - 1;
                    if (isWorldArea(scaledMouseX, scaledMouseY) && state.camera.isActive) {
                        interactionHandler.handleWorldClick(1, displayMouseX, displayMouseY);
                    }
                }
            }
            // 中键拖拽平移：按下瞬间先清除鼠标缓冲区残留，防止单击时产生意外位移
            if (Mouse.isButtonDown(2)) {
                if (!middleMouseDown) {
                    middleMouseDown = true;
                    middleDragDistance = 0.0D;
                    Mouse.getDX();
                    Mouse.getDY();
                }
                int dx = Mouse.getDX();
                int dy = Mouse.getDY();
                if (dx != 0 || dy != 0) {
                    middleDragDistance += Math.abs(dx) + Math.abs(dy);
                    if (middleDragDistance > MIDDLE_DRAG_THRESHOLD) {
                        cameraInputHelper.addDragPan(dx, -dy);
                    }
                }
            } else {
                middleMouseDown = false;
            }

            // 检测左键释放 → 发送挖掘 abort
            if (!Mouse.isButtonDown(0) && leftMouseDown) {
                leftMouseDown = false;
                // P0-3: 无论连锁模式与否，左键释放时总是发送abort
                // 连锁挖掘的持续性由 C2SRtsUltimineMessage 独立管理
                interactionHandler.abortMine();
                // 立即清除客户端裂纹状态
                state.interaction.mineProgressX = -1;
                state.interaction.mineProgressY = -1;
                state.interaction.mineProgressZ = -1;
                state.interaction.mineProgressStage = 0;
                // 清除连锁挖掘进度
                state.interaction.ultimineProgressProcessed = -1;
                state.interaction.ultimineProgressTotal = 0;
                mineStartedTick = -1;
            }
        }

        // ---- 面板滚轮分发 ----
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            float invZoom = 1.0f / state.uiZoom;
            int mouseX = (int) ((Mouse.getEventX() * width / mc.displayWidth) * invZoom);
            int mouseY = (int) ((height - Mouse.getEventY() * height / mc.displayHeight - 1) * invZoom);
            inputRouter.dispatchScroll(mouseX, mouseY, scroll > 0 ? 1 : -1);

            // 修复: 快速建造NEED_HEIGHT阶段滚轮调整高度偏移
            if (state.interaction.quickBuildActive && state.interaction.shapeBuildSession != null
                && state.interaction.shapeBuildSession.phase
                    == com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildPhase.NEED_HEIGHT) {
                state.interaction.shapeBuildSession.heightOffset += (scroll > 0 ? 1 : -1);
                state.interaction.shapeBuildSession.heightOffset = Math
                    .max(-32, Math.min(32, state.interaction.shapeBuildSession.heightOffset));
            }
        }

        // Bug1修复：齿轮面板拖拽更新
        if (gearMenuPanel.isOpen()) {
            float invZoom = 1.0f / state.uiZoom;
            int scaledMouseX = (int) ((Mouse.getX() * width / mc.displayWidth) * invZoom);
            int scaledMouseY = (int) ((height - Mouse.getY() * height / mc.displayHeight - 1) * invZoom);
            if (Mouse.isButtonDown(0)) {
                gearMenuPanel.handleDragUpdate(scaledMouseX, scaledMouseY);
            } else {
                gearMenuPanel.stopDrag();
            }
        }

        // Bug2修复：窗口面板拖拽/释放分发
        dispatchWindowPanelDrag();
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == 1) { // ESC
            // Bug3修复：ESC 关闭时发送toggle禁用相机，关闭屏幕
            // 注意：onGuiClosed 中会做持久化清理
            if (state.camera.isActive) {
                RtsNetworkManager.NETWORK
                    .sendToServer(new com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraMessage(true));
                state.camera.isActive = false;
            }
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) {
                mc.setIngameFocus();
            }
            return;
        }
        if (!inputRouter.dispatchKey(c, keyCode)) {
            super.keyTyped(c, keyCode);
        }
    }

    @Override
    public void onGuiClosed() {
        // Bug2修复：清理挖掘状态（关闭 GUI 时释放左键）
        if (leftMouseDown) {
            interactionHandler.abortMine();
            leftMouseDown = false;
        }
        // Bug6修复：如果是被远程菜单替换（相机仍活跃），保留 localMirror
        // 仅当相机主动退出时才清理 localMirror
        if (!state.camera.isActive) {
            state.camera.localMirror = null;
        }
        state.persist();
        // Bug6修复：如果是被远程菜单替换，保留已请求的数据标记，避免重新请求
        if (!state.camera.isActive) {
            initialDataRequested = false;
            lastRequestedPage = -1;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ======== 面板管理 ========

    public void addPanel(IRtsPanel panel) {
        panels.put(panel.panelName(), panel);
        inputRouter.registerPanel(panel);
    }

    public void removePanel(String name) {
        panels.remove(name);
        inputRouter.unregisterPanel(name);
    }

    public IRtsPanel getPanel(String name) {
        return panels.get(name);
    }

    // ======== 访问器 ========

    public RtsClientState getState() {
        return state;
    }

    public RtsInputRouter getInputRouter() {
        return inputRouter;
    }

    public CameraInputHelper getCameraInputHelper() {
        return cameraInputHelper;
    }

    public RtsInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    /** Bug1修复：获取齿轮设置面板 */
    public GearMenuPanel getGearMenuPanel() {
        return gearMenuPanel;
    }

    /** Bug2修复：获取快速建造面板 */
    public QuickBuildPanel getQuickBuildPanel() {
        return quickBuildPanel;
    }

    /** Bug2修复：获取连锁挖掘面板 */
    public UltiminePanel getUltiminePanel() {
        return ultiminePanel;
    }

    public FunnelPanel getFunnelPanel() {
        return funnelPanel;
    }

    /** Bug2修复：向所有窗口面板分发拖拽/释放事件 */
    private void dispatchWindowPanelDrag() {
        float invZoom = 1.0f / state.uiZoom;
        int scaledX = (int) ((Mouse.getX() * width / mc.displayWidth) * invZoom);
        int scaledY = (int) ((height - Mouse.getY() * height / mc.displayHeight - 1) * invZoom);
        boolean leftDown = Mouse.isButtonDown(0);

        for (IRtsPanel panel : panels.values()) {
            if (panel instanceof RtsWindowPanel) {
                RtsWindowPanel win = (RtsWindowPanel) panel;
                if (leftDown) {
                    win.mouseDragged(scaledX, scaledY);
                } else {
                    win.mouseReleased(scaledX, scaledY);
                }
            }
        }
    }

    // ======== Bug5修复：本地镜像相机 ========

    /**
     * 确保本地镜像相机实体存在。
     * 对标原版 ClientRtsController.ensureLocalMirrorCamera()。
     * 在服务端回包之前，立即创建客户端相机实体并设为 renderViewEntity。
     */
    private void ensureLocalMirrorCamera() {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;
        if (mc.theWorld == null) {
            camera.localMirror = null;
            return;
        }

        if (camera.localMirror != null && camera.localMirror.worldObj == mc.theWorld) {
            return;
        }

        camera.localMirror = new RtsCameraEntity(mc.theWorld);
        camera.localMirror.setPosition(camera.posX, camera.posY, camera.posZ);
        camera.localMirror.rotationYaw = camera.rotationYaw;
        camera.localMirror.rotationPitch = camera.rotationPitch;

        // 如果当前渲染视角仍是玩家，立即切换到本地镜像
        if (mc.renderViewEntity == null || mc.renderViewEntity == mc.thePlayer) {
            mc.renderViewEntity = camera.localMirror;
        }
    }

    /**
     * 将 CameraViewModel 的姿态同步到本地镜像相机实体+服务端相机实体。
     * 对标原版 ClientRtsController.snapLocalMirrorCameraPose()。
     */
    private void snapLocalMirrorCameraPose() {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;
        double x = camera.posX, y = camera.posY, z = camera.posZ;
        float yaw = camera.rotationYaw, pitch = camera.rotationPitch;
        if (camera.localMirror != null) {
            camera.localMirror.snapTo(x, y, z, yaw, pitch);
        }
        // Bug5修复：同时更新服务端相机实体（如果已由 S2C 同步），确保移动渲染连续
        if (camera.cameraEntity != null) {
            camera.cameraEntity.snapTo(x, y, z, yaw, pitch);
        }
    }
}
