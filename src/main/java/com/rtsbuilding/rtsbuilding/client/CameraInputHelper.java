package com.rtsbuilding.rtsbuilding.client;

import net.minecraft.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.rtsbuilding.rtsbuilding.ClientProxy;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMoveMessage;

/**
 * 相机输入辅助 — 处理 RTS 相机完整的客户端输入管线。
 *
 * 阶段A 重写：
 * - WASD/上下 键盘输入采集
 * - EMA 平滑旋转（pendingRaw → emaRotate）
 * - 灵敏度缩放
 * - 滚轮 dolly 采集
 * - 鼠标拖拽旋转/平移采集
 * - 零输入衰减
 * - 构建完整 10 字段 C2S 移动消息
 *
 * 阶段A 收尾：
 * - 搜索框聚焦时抑制 WASD 输入
 * - applyLocalPrediction 完善：滚轮 dolly + 边界 clamping
 *
 * Bug1+Bug2 修复 (2026-06-11)：
 * - applyFullLocalPrediction：添加旋转本地预测 + 修正移动方向公式 + 拖拽平移
 * - 与原版 ClientRtsController.applyLocalPrediction() 对齐
 *
 * Bug3 修复 (2026-06-11)：
 * - 添加 beginRightPress/endRightPress 右键状态管理（对齐原版 CameraInputHandler）
 * - 右键世界交互从 mouseClicked(按下) 迁移到 mouseReleased(释放) 触发
 * - handleRightDrag 追踪拖拽距离以区分点击/拖拽
 */
public class CameraInputHelper {

    private static final float EPSILON = 1.0e-4f;
    private static final float SENSITIVITY_SCALE = 0.5f;
    /** 旋转增益（对齐原版 ROTATE_GAIN_X / ROTATE_GAIN_Y） */
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;
    /** 滚轮 dolly 缩放常量（同服务端 DOLLY_PER_SCROLL = 2.6） */
    private static final double DOLLY_PER_SCROLL = 2.6D;
    /** 本地预测速度：normal */
    private static final double SPEED_NORMAL = 0.45D;
    /** 本地预测速度：fast */
    private static final double SPEED_FAST = 0.80D;
    /** 本地预测垂直速度 */
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;

    /** 右键拖拽阈值（像素），对齐原版 MIDDLE_CLICK_DRAG_THRESHOLD = 1.5D */
    private static final double RIGHT_DRAG_THRESHOLD = 1.5D;

    private final RtsClientState state;

    // ── Bug3 修复：右键拖拽状态管理（对齐原版 CameraInputHandler） ──

    /** 右键拖拽是否激活 */
    private boolean rightPressActive = false;
    /** 触发右键拖拽的鼠标按钮 */
    private int rightPressButton = -1;
    /** 当前右键是否可触发主要动作（放置/交互） */
    private boolean rightPressCanPrimary = false;
    /** 当前右键是否可触发旋转 */
    private boolean rightPressCanRotate = false;
    /** 是否已发生旋转拖拽（用于区分点击和拖拽） */
    private boolean rightDragRotated = false;
    /** Bug4修复：右键按下起始坐标（用于绝对距离判断，避免帧增量误判） */
    private double rightPressStartX = 0.0D;
    private double rightPressStartY = 0.0D;
    /** 右键拖拽绝对距离（从按下点起算） */
    private double rightDragDistance = 0.0D;

    public CameraInputHelper() {
        this.state = RtsClientState.get();
    }

    /**
     * 每 tick 调用：收集所有输入并处理 EMA 平滑。
     * 搜索框聚焦时抑制 WASD 移动，但保留上下键用于高度调节。
     */
    public void updateInputFromKeyBindings() {
        if (!state.camera.isActive) return;

        CameraViewModel camera = state.camera;

        // ---- 键盘输入采集 ----
        // 搜索框聚焦时抑制 WASD 移动（但保留 Q/E 上下移动）
        if (camera.searchBoxFocused) {
            camera.movingForward = false;
            camera.movingBackward = false;
            camera.movingLeft = false;
            camera.movingRight = false;
        } else {
            camera.movingForward = Keyboard.isKeyDown(ClientProxy.keyCameraForward.getKeyCode());
            camera.movingBackward = Keyboard.isKeyDown(ClientProxy.keyCameraBack.getKeyCode());
            camera.movingLeft = Keyboard.isKeyDown(ClientProxy.keyCameraLeft.getKeyCode());
            camera.movingRight = Keyboard.isKeyDown(ClientProxy.keyCameraRight.getKeyCode());
        }
        // 上下键始终可用（搜索框中用于高度微调）
        camera.movingUp = Keyboard.isKeyDown(ClientProxy.keyCameraUp.getKeyCode())
            || Keyboard.isKeyDown(Keyboard.KEY_SPACE);
        camera.movingDown = Keyboard.isKeyDown(ClientProxy.keyCameraDown.getKeyCode());

        // ---- 滚轮采集 ----
        int rawDwheel = Mouse.getDWheel();
        if (rawDwheel != 0) {
            camera.pendingScroll += (rawDwheel / 120.0f) * camera.sensitivity;
        }

        // ---- EMA 平滑旋转处理 ----
        camera.applyEmaSmoothing();
    }

    public void addDragRotate(float dx, float dy) {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;

        // Bug4修复：移除双击灵敏度 — 原始 dx/dy 直接入队，
        // 灵敏度缩放统一在 getScaledEmaRotateX/Y() 中单次应用。
        // 对齐原版 ClientRtsController.queueRotateDrag() 的单次灵敏度行为。
        camera.pendingRawRotateX += dx;
        camera.pendingRawRotateY += dy;
    }

    public void addDragPan(float dx, float dy) {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;

        // 同样的策略：移除双击灵敏度
        camera.pendingPanX += dx;
        camera.pendingPanY += dy;
    }

    /**
     * Bug1+Bug2修复：本地预测完整版。
     *
     * 在发送网络消息之前调用，将当前所有输入（键盘移动+旋转+滚轮+拖拽平移）
     * 应用到本地相机状态，实现即时视觉反馈。
     * 对齐原版 ClientRtsController.applyLocalPrediction() 的完整逻辑。
     *
     * @param tickDelta 帧时间缩放因子（1.0 = 标准tick速率，<1.0 = 慢帧，>1.0 = 快帧）
     *                  平滑镜头模式下基于纳秒帧时间差计算，非平滑模式下为 1.0
     */
    public void applyFullLocalPrediction(float tickDelta) {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;

        float forward = (camera.movingForward ? 1 : 0) - (camera.movingBackward ? 1 : 0);
        float strafe = (camera.movingLeft ? 1 : 0) - (camera.movingRight ? 1 : 0);
        float vertical = (camera.movingUp ? 1 : 0) - (camera.movingDown ? 1 : 0);
        float rotateX = camera.getScaledEmaRotateX();
        float rotateY = camera.getScaledEmaRotateY();
        float scroll = camera.pendingScroll;
        float panX = camera.pendingPanX;
        float panY = camera.pendingPanY;
        boolean fast = isFastKeyDown();

        // 无输入则跳过
        if (forward == 0 && strafe == 0
            && vertical == 0
            && Math.abs(rotateX) < EPSILON
            && Math.abs(rotateY) < EPSILON
            && Math.abs(scroll) < EPSILON
            && Math.abs(panX) < EPSILON
            && Math.abs(panY) < EPSILON) return;

        // ---- Bug2a修复：旋转本地预测（tickDelta缩放实现帧率无关） ----
        camera.rotationYaw += rotateX * ROTATE_GAIN_X * tickDelta;
        camera.rotationPitch = MathHelper
            .clamp_float(camera.rotationPitch + rotateY * ROTATE_GAIN_Y * tickDelta, MIN_PITCH, MAX_PITCH);

        // ---- 移动计算 ----
        double yawRad = Math.toRadians(camera.rotationYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double speed = (fast ? SPEED_FAST : SPEED_NORMAL) * camera.sensitivity;

        double dx = 0, dy = 0, dz = 0;

        // ---- Bug2修复：WASD 水平移动（与原版 ClientRtsController 对齐） ----
        // strafe = A-D（A=+1 向左, D=-1 向右），与原版 CameraInput 约定一致
        if (forward != 0 || strafe != 0) {
            dx += (-sinYaw * forward + cosYaw * strafe) * speed;
            dz += (cosYaw * forward + sinYaw * strafe) * speed;
        }

        // ---- 垂直移动 ----
        if (vertical != 0) {
            dy += vertical * (fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        }

        // ---- 滚轮沿视线 dolly ----
        if (Math.abs(scroll) > EPSILON) {
            double pitchRad = Math.toRadians(camera.rotationPitch);
            double lookX = -sinYaw * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = cosYaw * Math.cos(pitchRad);

            double dolly = scroll * DOLLY_PER_SCROLL;
            dx += lookX * dolly;
            dy += lookY * dolly;
            dz += lookZ * dolly;
        }

        // ---- 拖拽平移（对齐原版 pan drag 公式） ----
        if (Math.abs(panX) > EPSILON || Math.abs(panY) > EPSILON) {
            double dragScale = 0.020D * Math.max(8.0D, camera.heightOffset);
            double moveRight = panX * dragScale;
            double moveForward = -panY * dragScale;

            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);

            dx += rightX * moveRight + fwdX * moveForward;
            dz += rightZ * moveRight + fwdZ * moveForward;
        }

        // ---- 应用移动（tickDelta 缩放实现帧率无关的平滑移动） ----
        camera.posX += dx * tickDelta;
        camera.posY += dy * tickDelta;
        camera.posZ += dz * tickDelta;

        // ---- 边界 clamping ----
        double halfExtent = camera.maxRadius;
        camera.posX = MathHelper.clamp_double(camera.posX, camera.anchorX - halfExtent, camera.anchorX + halfExtent);
        camera.posZ = MathHelper.clamp_double(camera.posZ, camera.anchorZ - halfExtent, camera.anchorZ + halfExtent);
        camera.posY = MathHelper.clamp_double(camera.posY, camera.anchorY - 35.0, camera.anchorY + 110.0);

        // ---- 更新高度偏移 ----
        camera.heightOffset = camera.posY - camera.anchorY;

        // ---- 更新相机实体位置（即时视觉反馈） ----
        if (camera.cameraEntity != null) {
            camera.cameraEntity.snapTo(camera.posX, camera.posY, camera.posZ, camera.rotationYaw, camera.rotationPitch);
        }
    }

    public boolean sendMoveMessageOnInput() {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return false;

        float forward = (camera.movingForward ? 1 : 0) - (camera.movingBackward ? 1 : 0);
        float strafe = (camera.movingLeft ? 1 : 0) - (camera.movingRight ? 1 : 0);
        float vertical = (camera.movingUp ? 1 : 0) - (camera.movingDown ? 1 : 0);

        float rotateX = camera.getScaledEmaRotateX();
        float rotateY = camera.getScaledEmaRotateY();
        float scroll = camera.pendingScroll;
        float panX = camera.pendingPanX;
        float panY = camera.pendingPanY;
        boolean fast = isFastKeyDown();

        boolean hasInput = Math.abs(forward) > EPSILON || Math.abs(strafe) > EPSILON
            || Math.abs(vertical) > EPSILON
            || Math.abs(rotateX) > EPSILON
            || Math.abs(rotateY) > EPSILON
            || Math.abs(scroll) > EPSILON
            || Math.abs(panX) > EPSILON
            || Math.abs(panY) > EPSILON;

        if (hasInput) {
            camera.resetHeartbeat();
        }

        C2SRtsCameraMoveMessage msg = new C2SRtsCameraMoveMessage(
            forward,
            strafe,
            vertical,
            panX,
            panY,
            rotateX,
            rotateY,
            scroll,
            0,
            fast);

        RtsNetworkManager.NETWORK.sendToServer(msg);

        // Bug2b修复：pending值清零移到这里（本地预测已先执行）
        camera.pendingScroll = 0;
        camera.pendingPanX = 0;
        camera.pendingPanY = 0;

        return hasInput;
    }

    public void sendHeartbeat() {
        CameraViewModel camera = state.camera;
        if (!camera.isActive) return;

        boolean fast = isFastKeyDown();

        C2SRtsCameraMoveMessage msg = new C2SRtsCameraMoveMessage(0, 0, 0, 0, 0, 0, 0, 0, 0, fast);
        RtsNetworkManager.NETWORK.sendToServer(msg);

        camera.pendingScroll = 0;
        camera.pendingPanX = 0;
        camera.pendingPanY = 0;
    }

    // ======================== Bug3 修复：右键拖拽状态管理 ========================

    /**
     * 开始右键拖拽（对齐原版 CameraInputHandler.beginRightPress）。
     * 在 mouseClicked 中调用，仅记录状态，不触发世界交互。
     */
    public void beginRightPress(double mouseX, double mouseY, int button) {
        this.rightPressActive = true;
        this.rightPressButton = button;
        this.rightPressCanPrimary = true; // 右键既是 primary 也是 rotate
        this.rightPressCanRotate = true;
        this.rightDragRotated = false;
        // Bug4修复：保存按下起始坐标，用于绝对距离判断
        this.rightPressStartX = mouseX;
        this.rightPressStartY = mouseY;
        this.rightDragDistance = 0.0D;
    }

    /**
     * 结束右键拖拽，返回 true 表示应触发世界交互（对齐原版 CameraInputHandler.endRightPress）。
     * 仅当拖拽未发生旋转时返回 true。
     *
     * @return true 如果这是一次点击（非拖拽），应触发 runPrimaryActionAt
     */
    public boolean endRightPress(double mouseX, double mouseY, int button) {
        if (!this.rightPressActive || button != this.rightPressButton) {
            return false;
        }
        boolean canPrimary = this.rightPressCanPrimary;
        this.rightPressActive = false;
        this.rightPressButton = -1;
        this.rightPressCanPrimary = false;
        this.rightPressCanRotate = false;
        if (this.rightDragRotated) {
            this.rightDragRotated = false;
            this.rightDragDistance = 0.0D;
            return false; // 已发生旋转，不触发动作
        }
        this.rightDragDistance = 0.0D;
        return canPrimary;
    }

    /**
     * 处理右键拖拽旋转（对齐原版 CameraInputHandler.handleRightDrag）。
     * 累积拖拽距离，超过阈值标记为旋转。
     * 
     * 1. 添加世界区域检查 (inWorldArea)，仅在3D世界区域消费旋转
     * 2. 仅在拖拽距离超出阈值后才调用 addDragRotate，避免单击微抖动触发旋转
     * 3. 添加 Alt 键抑制检查
     *
     * @param inWorldArea 鼠标是否在3D世界区域（非UI面板区域）
     * @return true 如果拖拽被消费（旋转）
     */
    public boolean handleRightDrag(double mouseX, double mouseY, int button, double dragX, double dragY,
        boolean inWorldArea) {
        if (!this.rightPressActive || button != this.rightPressButton || !this.rightPressCanRotate) {
            return false;
        }
        if (!inWorldArea || isAltDown()) {
            return false;
        }
        this.rightDragDistance = Math.abs(mouseX - this.rightPressStartX) + Math.abs(mouseY - this.rightPressStartY);
        if (this.rightDragDistance > RIGHT_DRAG_THRESHOLD) {
            this.rightDragRotated = true;
            addDragRotate((float) dragX, (float) -dragY);
        }
        return true;
    }

    public boolean isRightPressActive() {
        return this.rightPressActive;
    }

    public int getRightPressButton() {
        return this.rightPressButton;
    }

    public boolean isRightDragRotated() {
        return this.rightDragRotated;
    }

    public double getRightDragDistance() {
        return this.rightDragDistance;
    }

    private static boolean isFastKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    /** Alt 键按下检查（对齐原版 isAltDown） */
    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }
}
