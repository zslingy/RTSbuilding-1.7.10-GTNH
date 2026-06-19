package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 相机 ViewModel — 管理 RTS 相机的完整客户端状态。
 *
 * 阶段A 扩展：添加 EMA 平滑状态、灵敏度预设、心跳计数器、滚轮/拖拽输入状态。
 * 阶段A 收尾：添加原版视角保存/恢复字段。
 * Bug2修复：添加 cameraEntity 引用，供本地预测直接更新实体位置。
 */
public class CameraViewModel {

    // ---- 相机位置 ----
    public double posX, posY, posZ;
    public float rotationYaw, rotationPitch;
    public float zoom = 1.0f;

    /** 输入灵敏度预设数组（同原版） */
    public static final float[] INPUT_SENSITIVITY_PRESETS = { 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f };
    /** 当前灵敏度预设索引（默认 = 1.00） */
    public int inputSensitivityIndex = 2;
    public float sensitivity = 1.0f;

    // ---- 边界 ----
    public BlockPos boundsMin = null;
    public BlockPos boundsMax = null;
    /** 操作半径 */
    public double maxRadius = 128.0;
    /** 高度偏移（cameraY - anchorY） */
    public double heightOffset = 18.0;
    /** 锚点坐标 */
    public double anchorX, anchorY, anchorZ;

    public void setAnchorPosition(double x, double y, double z) {
        anchorX = x;
        anchorY = y;
        anchorZ = z;
    }

    // ---- 状态 ----
    public boolean isActive = false;
    public boolean homeSelection = false;
    public boolean closeRangeAllowed = false;

    // ---- 移动状态（键盘输入） ----
    public boolean movingForward = false;
    public boolean movingBackward = false;
    public boolean movingLeft = false;
    public boolean movingRight = false;
    public boolean movingUp = false;
    public boolean movingDown = false;

    // ---- EMA 平滑旋转（阶段A新增） ----
    /** 原始待处理旋转输入 */
    public float pendingRawRotateX, pendingRawRotateY;
    /** EMA 平滑后的旋转值 */
    public float emaRotateX, emaRotateY;
    /** EMA 平滑因子 */
    private static final float EMA_ALPHA = 0.28f;
    /** 零输入衰减因子 */
    private static final float ZERO_INPUT_DECAY = 0.78f;
    /** 低于此阈值归零 */
    private static final float EPSILON = 1.0e-4f;

    // ---- 滚轮/拖拽输入（阶段A新增） ----
    /** 滚轮累积输入（dolly zoom） */
    public float pendingScroll;
    /** 鼠标拖拽平移输入 */
    public float pendingPanX, pendingPanY;
    /** 是否正在拖拽旋转（右键） */
    public boolean rotateDragActive;
    /** 是否正在拖拽平移（中键） */
    public boolean panDragActive;

    // ---- 心跳机制（阶段A新增） ----
    /** 相机移动心跳计数器（tick 计数） */
    public int cameraMoveHeartbeatTicks = 0;
    /** 每 20 ticks 发送一次心跳 */
    public static final int CAMERA_IDLE_HEARTBEAT_TICKS = 20;
    /** 快模式标志 */
    public boolean fastMode = false;

    // ---- 透视状态 ----
    public boolean perspectiveActive = false;

    // ---- 原版视角保存/恢复（阶段A收尾） ----
    private int savedThirdPersonView = 0;
    private float savedFovSetting = 70.0f;
    private boolean savedViewBobbing = true;
    private boolean vanillaStateSaved = false;

    /** 搜索框是否聚焦（用于抑制 WASD 输入） */
    public boolean searchBoxFocused = false;

    /** Bug6修复：远程菜单 grace 倒计时（ticks），用于远程菜单打开/关闭过渡期 */
    public int remoteMenuGraceTicks = 0;

    /**
     * Bug1修复: 远程菜单打开前的等待 grace（对齐原版 pendingRemoteMenuOpenTicks = 80）
     * 在发送可能打开远程容器的交互消息后设置，防止 RTS 屏幕被过早恢复导致闪烁
     */
    public int pendingRemoteMenuOpenTicks = 0;

    // ---- Bug2修复：相机实体引用（供本地预测用） ----
    /** 服务端创建的相机实体引用，本地预测时直接更新其位置 */
    public RtsCameraEntity cameraEntity = null;

    // ---- Bug2修复：本地镜像相机实体（纯客户端渲染用） ----
    /**
     * 纯客户端本地镜像相机实体。
     * 不依赖服务端回包，RTS 模式激活后立即创建并设为 renderViewEntity，
     * 实现即时相机视角切换。
     * 对齐原版 ClientRtsController.localMirrorCamera。
     */
    public RtsCameraEntity localMirror = null;

    // ---- Bug5修复：帧级插值移动（对齐原版 smoothCamera） ----
    /** 上一帧的纳秒时间戳，用于计算帧时间差 */
    public long lastFrameNanos = 0L;
    /** 每 tick 的纳秒数（50ms = 50_000_000ns） */
    private static final long NANOS_PER_TICK = 50_000_000L;
    /** 最大 tickDelta（防止跳帧过大） */
    private static final float MAX_SMOOTH_FRAME_TICKS = 2.00F;

    // ---- 边界计算 ----
    public boolean hasBounds() {
        return boundsMin != null && boundsMax != null && isActive;
    }

    public int getRadiusBlocks() {
        if (!hasBounds()) return 0;
        int dx = boundsMax.getX() - boundsMin.getX();
        int dz = boundsMax.getZ() - boundsMin.getZ();
        return Math.max(dx, dz) / 2;
    }

    // ---- EMA 平滑方法（阶段A新增） ----

    public void applyEmaSmoothing() {
        float safeRawX = pendingRawRotateX;
        emaRotateX += (safeRawX - emaRotateX) * EMA_ALPHA;

        float safeRawY = pendingRawRotateY;
        emaRotateY += (safeRawY - emaRotateY) * EMA_ALPHA;

        if (Math.abs(pendingRawRotateX) < EPSILON) {
            emaRotateX *= ZERO_INPUT_DECAY;
        }
        if (Math.abs(pendingRawRotateY) < EPSILON) {
            emaRotateY *= ZERO_INPUT_DECAY;
        }

        if (Math.abs(emaRotateX) < EPSILON) emaRotateX = 0;
        if (Math.abs(emaRotateY) < EPSILON) emaRotateY = 0;
    }

    public float getScaledEmaRotateX() {
        return emaRotateX * sensitivity;
    }

    public float getScaledEmaRotateY() {
        return emaRotateY * sensitivity;
    }

    public boolean tickHeartbeat() {
        cameraMoveHeartbeatTicks++;
        if (cameraMoveHeartbeatTicks >= CAMERA_IDLE_HEARTBEAT_TICKS) {
            cameraMoveHeartbeatTicks = 0;
            return true;
        }
        return false;
    }

    public void resetHeartbeat() {
        cameraMoveHeartbeatTicks = 0;
    }

    // ---- 帧级状态重置 ----
    public void resetFrameState() {
        pendingRawRotateX = 0;
        pendingRawRotateY = 0;
        pendingScroll = 0;
        pendingPanX = 0;
        pendingPanY = 0;
    }

    // ---- 原版视角保存/恢复（阶段A收尾） ----

    public void saveVanillaCameraState(net.minecraft.client.Minecraft mc) {
        if (vanillaStateSaved) return;
        if (mc == null || mc.gameSettings == null) return;

        savedThirdPersonView = mc.gameSettings.thirdPersonView;
        savedFovSetting = mc.gameSettings.fovSetting;
        savedViewBobbing = mc.gameSettings.viewBobbing;
        vanillaStateSaved = true;

        mc.gameSettings.thirdPersonView = 0;
    }

    public void restoreVanillaCameraState(net.minecraft.client.Minecraft mc) {
        if (!vanillaStateSaved) return;
        if (mc == null || mc.gameSettings == null) return;

        mc.gameSettings.thirdPersonView = savedThirdPersonView;
        mc.gameSettings.fovSetting = savedFovSetting;
        mc.gameSettings.viewBobbing = savedViewBobbing;
        vanillaStateSaved = false;
    }

    public void resetForNewSession() {
        isActive = false;
        boundsMin = null;
        boundsMax = null;
        posX = posY = posZ = 0;
        rotationYaw = rotationPitch = 0;
        zoom = 1.0f;
        sensitivity = INPUT_SENSITIVITY_PRESETS[2];
        inputSensitivityIndex = 2;
        maxRadius = 128.0;
        heightOffset = 18.0;
        anchorX = anchorY = anchorZ = 0;
        movingForward = movingBackward = movingLeft = movingRight = movingUp = movingDown = false;
        perspectiveActive = false;
        homeSelection = false;
        closeRangeAllowed = false;
        pendingRawRotateX = pendingRawRotateY = 0;
        emaRotateX = emaRotateY = 0;
        pendingScroll = 0;
        pendingPanX = pendingPanY = 0;
        rotateDragActive = false;
        panDragActive = false;
        cameraMoveHeartbeatTicks = 0;
        fastMode = false;
        vanillaStateSaved = false;
        searchBoxFocused = false;
        cameraEntity = null;
    }
}
