package com.rtsbuilding.rtsbuilding.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildPhase;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildSession;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.common.BuildShape;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsBreakMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsRotateBlockMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStorageMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsUnlinkStorageMessage;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * RTS 世界交互处理器 — 从相机实体发射射线，根据交互模式发送建造/破坏/交互消息。
 *
 * 修复 Bug1 (2026-06-11)：光标位置光线投射对齐原版 ScreenCursorPicker.computeCursorRayDirection()。
 * 旧版本使用相机视线中央做射线检测，修复后基于鼠标在屏幕上的实际位置计算射线方向。
 *
 * 按钮映射（对齐原版 ClientKeyMappings）：
 * 左键 (button 0) = ACTION_BREAK → 破坏方块
 * 右键 (button 1) = ACTION_PRIMARY → 放置方块 / 空手交互（打开GUI等）
 * 中键 (button 2) = PICK_BLOCK/CAMERA_PAN_DRAG（暂不处理）
 */
public class RtsInteractionHandler {

    private static final double RAY_REACH = 200.0D;

    private final RtsClientState state;

    public RtsInteractionHandler() {
        this.state = RtsClientState.get();
    }

    /**
     * 处理世界区域鼠标点击。
     *
     * @param button 0=左键(破坏), 1=右键(放置/交互), 2=中键
     * @param mouseX 屏幕 X 坐标（像素）
     * @param mouseY 屏幕 Y 坐标（像素）
     * @return true 如果点击已被消费
     */
    public boolean handleWorldClick(int button, int mouseX, int mouseY) {
        RtsCameraEntity camera = state.camera.cameraEntity;
        // Bug2修复：cameraEntity 为 null 时降级到本地镜像相机（服务端回包前的时间窗口）
        if (camera == null) {
            camera = state.camera.localMirror;
        }
        if (camera == null) return false;

        World world = camera.worldObj;
        if (world == null) {
            // localMirror 没有 worldObj 时尝试用 mc.theWorld 直接做射线检测
            world = Minecraft.getMinecraft().theWorld;
            if (world == null) return false;
        }

        Minecraft mc = Minecraft.getMinecraft();

        double eyeY = camera.posY + camera.getEyeHeight();
        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);

        // Bug1修复：使用光标位置计算射线方向（对齐原版 ScreenCursorPicker）
        Vec3 look = computeCursorRay(camera, mouseX, mouseY, mc);
        Vec3 end = start.addVector(look.xCoord * RAY_REACH, look.yCoord * RAY_REACH, look.zCoord * RAY_REACH);

        MovingObjectPosition hit = world.rayTraceBlocks(start, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        BuilderMode mode = state.interaction.currentMode;

        // Bug3修复：FUNNEL 模式下左右键不触发任何世界交互（自动漏斗由每 tick 的射线检测驱动）
        if (mode == BuilderMode.FUNNEL) {
            return true;
        }

        // Bug3修复：LINK_STORAGE 模式优先路由 — 左键绑定/取消绑定，右键仅提取
        if (mode == BuilderMode.LINK_STORAGE) {
            return handleLinkStorageModeClick(button, hit);
        }

        // GUI 绑定捕获模式 → 右键世界方块绑定 GUI
        if (state.interaction.guiBindingCaptureActive && button == 1) {
            int slot = state.interaction.guiBindingCaptureSlot;
            state.interaction.guiBindingCaptureActive = false;
            state.interaction.guiBindingCaptureSlot = -1;
            RtsNetworkManager.NETWORK.sendToServer(
                new C2SRtsSetGuiBindingMessage(
                    slot,
                    "",
                    false,
                    hit.blockX,
                    hit.blockY,
                    hit.blockZ,
                    (byte) hit.sideHit));
            return true;
        }

        if (button == 0) {
            // 左键 = 破坏/挖矿
            return handleBreakClick(hit, start, look, mode);
        } else if (button == 1) {
            // 右键 = 放置/交互（根据模式和选中物品决定）
            return handlePlaceOrInteract(hit, start, look, mode);
        }
        // 中键暂不处理（由 handleMouseInput 中的拖拽逻辑处理）
        return false;
    }

    /**
     * Bug1修复：基于鼠标屏幕坐标计算通过该像素的3D射线方向。
     *
     * 对齐原版 ScreenCursorPicker.computeCursorRayDirection() 的算法：
     * 1. 将鼠标屏幕坐标归一化到 [-1, 1]
     * 2. 使用 FOV 和宽高比计算像素对应的角度偏移
     * 3. 从相机 forward 方向出发，叠加 right/up 偏移构建射线
     *
     * @param camera 相机实体
     * @param mouseX 屏幕 X 坐标（像素，左上角原点）
     * @param mouseY 屏幕 Y 坐标（像素，左上角原点）
     * @param mc     Minecraft 实例
     * @return 归一化的射线方向向量
     */
    public static Vec3 computeCursorRay(RtsCameraEntity camera, int mouseX, int mouseY, Minecraft mc) {
        int width = mc.displayWidth;
        int height = mc.displayHeight;

        // 归一化到 [-1, 1]，Y轴翻转（屏幕Y向下，世界Y向上）
        double nx = (mouseX / (double) width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / (double) height) * 2.0D;

        // 计算 FOV 半角正切值
        float fovSetting = mc.gameSettings.fovSetting;
        // 1.7.10 FOV 处理：乘以 70/原值以得到实际水平FOV
        double fovY = Math.toRadians(fovSetting); // 近似值
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / (double) height);

        // 获取相机基础方向向量
        float yaw = camera.rotationYaw;
        float pitch = camera.rotationPitch;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // forward = 相机视线方向
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = -Math.sin(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // right = forward × up（标准世界 up = (0,1,0)）
        double rightX = Math.cos(yawRad);
        double rightY = 0.0D;
        double rightZ = Math.sin(yawRad);

        // Bug3修复：up = forward × right（对齐原版 cross product，修复反号偏移）
        // forward × right = (-sin(y)sin(p), cos(p), cos(y)sin(p))
        double upX = -Math.sin(yawRad) * Math.sin(pitchRad);
        double upY = Math.cos(pitchRad);
        double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

        // 合成：ray = forward + (-nx)*tanX*right + ny*tanY*up（对齐原版符号）
        double rayX = fwdX + (-nx * tanX) * rightX + ny * tanY * upX;
        double rayY = fwdY + (-nx * tanX) * rightY + ny * tanY * upY;
        double rayZ = fwdZ + (-nx * tanX) * rightZ + ny * tanY * upZ;

        // 归一化
        double len = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
        if (len < 1.0e-9D) len = 1.0D;

        return Vec3.createVectorHelper(rayX / len, rayY / len, rayZ / len);
    }

    /**
     * 旧版中心视线射线（保留用于其他用途，如消息中需要视线方向）。
     */
    /**
     * Bug1修复：漏斗模式使用的静态射线检测方法。
     * 从相机实体发射射线，使用当前鼠标在屏幕上的实际位置。
     */
    public static net.minecraft.util.MovingObjectPosition pickBlockHit(RtsCameraEntity camera) {
        if (camera == null || camera.worldObj == null) return null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return null;
        int displayX = org.lwjgl.input.Mouse.getX();
        int displayY = mc.displayHeight - org.lwjgl.input.Mouse.getY() - 1;
        double eyeY = camera.posY + camera.getEyeHeight();
        Vec3 start = Vec3.createVectorHelper(camera.posX, eyeY, camera.posZ);
        Vec3 look = computeCursorRay(camera, displayX, displayY, mc);
        Vec3 end = start.addVector(look.xCoord * RAY_REACH, look.yCoord * RAY_REACH, look.zCoord * RAY_REACH);
        return camera.worldObj.rayTraceBlocks(start, end);
    }

    private static Vec3 getLookVec(RtsCameraEntity camera) {
        float yaw = camera.rotationYaw;
        float pitch = camera.rotationPitch;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return Vec3.createVectorHelper(x, y, z);
    }

    // ── 左键：破坏（Bug2修复：改为渐进采矿） ──

    private boolean handleBreakClick(MovingObjectPosition hit, Vec3 origin, Vec3 dir, BuilderMode mode) {
        Minecraft mc = Minecraft.getMinecraft();

        // Issue 4: 范围破坏模式下左键取消锚点会话
        if (state.interaction.ultimineActive && state.interaction.areaDestroyActive) {
            if (state.interaction.shapeBuildSession != null) {
                state.interaction.shapeBuildSession = null;
                return true;
            }
            return true; // 无session时也消费事件，避免触发挖掘
        }

        ItemStack heldTool = mc.thePlayer.getCurrentEquippedItem();
        String toolItemId = "";
        ItemStack toolPrototype = null;
        if (heldTool != null && heldTool.getItem() != null) {
            toolItemId = (String) cpw.mods.fml.common.registry.GameData.getItemRegistry()
                .getNameForObject(heldTool.getItem());
            toolPrototype = heldTool;
        }

        if (state.interaction.ultimineActive) {
            // 连锁挖掘：复用普通渐进挖掘，第一块破坏后触发链式破坏
            C2SRtsMineMessage msg = new C2SRtsMineMessage(
                hit.blockX,
                hit.blockY,
                hit.blockZ,
                (byte) hit.sideHit,
                true, // start = true
                (byte) mc.thePlayer.inventory.currentItem,
                toolItemId,
                toolPrototype,
                false, // allowPlacedBlockRecovery
                true); // ultimine = true
            RtsNetworkManager.NETWORK.sendToServer(msg);
        } else {
            // 普通单方块挖掘
            C2SRtsMineMessage msg = new C2SRtsMineMessage(
                hit.blockX,
                hit.blockY,
                hit.blockZ,
                (byte) hit.sideHit,
                true, // start = true
                (byte) mc.thePlayer.inventory.currentItem,
                toolItemId,
                toolPrototype,
                false); // allowPlacedBlockRecovery
            RtsNetworkManager.NETWORK.sendToServer(msg);
        }
        return true;
    }

    // ── 右键：放置 or 交互 ──

    private boolean handlePlaceOrInteract(MovingObjectPosition hit, Vec3 origin, Vec3 dir, BuilderMode mode) {
        // Issue 4: 快速建造模式 或 范围破坏模式 → 锚点形状操作
        if (state.interaction.quickBuildActive
            || (state.interaction.ultimineActive && state.interaction.areaDestroyActive)) {
            return handleShapeBuildRightClick(hit);
        }

        switch (mode) {
            case INTERACT:
                return handleInteractRightClick(hit, origin, dir);
            case ROTATE:
                return handleRotateClick(hit);
            // LINK_STORAGE 已移至 handleWorldClick() 中的独立路由
            case FUNNEL:
                boolean newState = !state.interaction.funnelActive;
                state.interaction.funnelActive = newState;
                C2SRtsSetFunnelMessage funnelMsg;
                if (newState && hit != null) {
                    funnelMsg = new C2SRtsSetFunnelMessage(
                        0,
                        0,
                        hit.blockX + 0.5D,
                        hit.blockY + 0.5D,
                        hit.blockZ + 0.5D,
                        true);
                } else {
                    funnelMsg = new C2SRtsSetFunnelMessage(newState ? 0 : -1, 0);
                }
                RtsNetworkManager.NETWORK.sendToServer(funnelMsg);
                return true;
            default:
                return false;
        }
    }

    /**
     * Issue 4: 统一的形状锚点操作 — 快速建造和范围破坏共用。
     * 阶段1: 创建session设置pointA
     * 阶段2: 设置pointB
     * 阶段3(可选): NEED_HEIGHT阶段(滚轮调整)
     * 阶段4: READY_CONFIRM — 右键确认放置/破坏
     */
    private boolean handleShapeBuildRightClick(MovingObjectPosition hit) {
        InteractionViewModel ivm = state.interaction;
        ShapeBuildSession session = ivm.shapeBuildSession;
        BuildShape shape = parseBuildShape(ivm.quickBuildShape);
        boolean isDestroy = ivm.areaDestroyActive;

        // BLOCK形状单次点击直接放置/破坏
        if (shape == BuildShape.BLOCK) {
            BlockPos pos = new BlockPos(hit.blockX, hit.blockY, hit.blockZ);
            if (isDestroy) {
                sendAreaDestroyBatch(java.util.Collections.singletonList(pos));
            } else {
                java.util.List<BlockPos> positions = new java.util.ArrayList<>();
                positions.add(pos);
                sendQuickBuildBatch(positions, hit);
            }
            return true;
        }

        if (session == null || session.phase == ShapeBuildPhase.IDLE) {
            // 阶段1: 创建session，设置pointA
            BlockPos pointA = new BlockPos(hit.blockX, hit.blockY, hit.blockZ);
            ivm.shapeBuildSession = new ShapeBuildSession(
                shape,
                pointA,
                hit.sideHit,
                ivm.quickBuildRotation * 15,
                ivm.quickBuildCylinder);
            return true;
        }

        if (session.phase == ShapeBuildPhase.NEED_SECOND_POINT) {
            // 阶段2: 设置pointB
            session.pointB = new BlockPos(hit.blockX, hit.blockY, hit.blockZ);
            // 判断是否需要高度阶段
            if (shape == BuildShape.WALL || shape == BuildShape.BOX
                || (shape == BuildShape.CIRCLE && session.cylinder)) {
                session.phase = ShapeBuildPhase.NEED_HEIGHT;
            } else {
                session.phase = ShapeBuildPhase.READY_CONFIRM;
            }
            return true;
        }

        if (session.phase == ShapeBuildPhase.NEED_HEIGHT) {
            // NEED_HEIGHT阶段: 右键确认高度
            session.phase = ShapeBuildPhase.READY_CONFIRM;
            return true;
        }

        if (session.phase == ShapeBuildPhase.READY_CONFIRM) {
            // 阶段4: 确认 — 生成所有位置并发送
            ShapeFillMode fillMode = ShapeFillMode.parse(ivm.quickBuildFill);
            java.util.List<BlockPos> positions = ShapeGeometryUtil
                .buildShapePositions(session, fillMode, ivm.lineSnap8Direction);
            if (isDestroy) {
                sendAreaDestroyBatch(positions);
            } else {
                sendQuickBuildBatch(positions, hit);
            }
            ivm.shapeBuildSession = null; // 重置
            return true;
        }

        return false;
    }

    /**
     * 发送快速建造批量放置消息 — 逐个发送C2SRtsPlaceMessage(quickBuild=true)。
     */
    private void sendQuickBuildBatch(java.util.List<BlockPos> positions, MovingObjectPosition hit) {
        String blockId = state.interaction.selectedBlockId;
        int meta = state.interaction.selectedBlockMeta;
        ItemStack prototype = createBlockStack(blockId, meta);

        for (BlockPos pos : positions) {
            C2SRtsPlaceMessage msg = new C2SRtsPlaceMessage(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                (byte) hit.sideHit,
                hit.hitVec.xCoord,
                hit.hitVec.yCoord,
                hit.hitVec.zCoord,
                (byte) 0,
                true, // P0-2: forcePlace = true（快速建造强制放置）
                true, // P0-2: skipIfOccupied = true（跳过已占用位置）
                blockId,
                prototype,
                0,
                0,
                0,
                0,
                0,
                0,
                true); // quickBuild = true
            RtsNetworkManager.NETWORK.sendToServer(msg);
        }
    }

    /**
     * Issue 4: 发送范围破坏批量消息 — 逐个发送C2SRtsBreakMessage。
     */
    private void sendAreaDestroyBatch(java.util.List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            C2SRtsBreakMessage msg = new C2SRtsBreakMessage(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                (byte) 0, // face
                true); // start = true
            RtsNetworkManager.NETWORK.sendToServer(msg);
        }
    }

    private static BuildShape parseBuildShape(String shapeName) {
        if (shapeName == null) return BuildShape.BLOCK;
        String s = shapeName.toUpperCase()
            .trim();
        switch (s) {
            case "LINE":
                return BuildShape.LINE;
            case "SQUARE":
                return BuildShape.SQUARE;
            case "WALL":
                return BuildShape.WALL;
            case "CIRCLE":
                return BuildShape.CIRCLE;
            case "BOX":
                return BuildShape.BOX;
            default:
                return BuildShape.BLOCK;
        }
    }

    /**
     * 交互模式右键：
     * - 有选中物品且是方块 → 放置方块
     * - 有选中物品但非方块（水桶、食物等） → 通过交互消息使用物品
     * - 空手 → 交互（打开方块GUI / 实体交互）
     */
    private boolean handleInteractRightClick(MovingObjectPosition hit, Vec3 origin, Vec3 dir) {
        String blockId = state.interaction.selectedBlockId;
        int meta = state.interaction.selectedBlockMeta;

        // P1-2: 空手交互：先检测实体，再检测方块
        if (blockId == null || blockId.isEmpty() || "minecraft:air".equals(blockId)) {
            int entityId = findEntityHit(origin, dir);
            if (entityId != C2SRtsInteractMessage.NO_ENTITY) {
                return sendInteractMessageWithEntity(entityId, hit, origin, dir);
            }
            return sendInteractMessage(hit, origin, dir, null);
        }

        // 有选中物品
        ItemStack prototype = createBlockStack(blockId, meta);
        if (prototype == null) {
            // Bug11修复: 非方块物品（水桶、铁桶、食物等）→ 先检测实体，再走 InteractMessage 路径
            // 关键改动：使用 C2SRtsInteractMessage（含完整临时传送逻辑）替代 C2SRtsUseItemMessage
            int entityId = findEntityHit(origin, dir);
            if (entityId != C2SRtsInteractMessage.NO_ENTITY) {
                return sendInteractMessageWithEntity(entityId, hit, origin, dir);
            }
            return sendInteractMessage(hit, origin, dir, blockId);
        }

        return sendPlaceMessage(hit, origin, dir, blockId, prototype);
    }

    private boolean sendPlaceMessage(MovingObjectPosition hit, Vec3 origin, Vec3 dir, String blockId,
        ItemStack prototype) {
        // Bug1修复: grace 期防闪烁（placement 可能触发容器变更是罕见的，但安全起见加上）
        com.rtsbuilding.rtsbuilding.ClientProxy.RtsKeyHandler.beginRemoteMenuOpenGrace();

        C2SRtsPlaceMessage msg = new C2SRtsPlaceMessage(
            hit.blockX,
            hit.blockY,
            hit.blockZ,
            (byte) hit.sideHit,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord,
            (byte) 0,
            false,
            false,
            blockId,
            prototype,
            origin.xCoord,
            origin.yCoord,
            origin.zCoord,
            dir.xCoord,
            dir.yCoord,
            dir.zCoord,
            false);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    /**
     * Bug11修复: 发送方块交互消息。支持传入 itemId 用于非方块物品的使用。
     * 
     * @param itemId 非空时表示使用 RTS 存储中选中的物品（如水桶），空则表示空手交互
     */
    private boolean sendInteractMessage(MovingObjectPosition hit, Vec3 origin, Vec3 dir, String itemId) {
        // Bug1修复: 通知 RTS key handler 进入 grace 期，防止远程容器打开期间 RTS 屏幕闪烁
        com.rtsbuilding.rtsbuilding.ClientProxy.RtsKeyHandler.beginRemoteMenuOpenGrace();

        byte toolSlot = (byte) state.interaction.selectedToolSlot;
        if (toolSlot < 0 || toolSlot >= 9) toolSlot = 0;
        C2SRtsInteractMessage msg = new C2SRtsInteractMessage(
            C2SRtsInteractMessage.NO_ENTITY,
            hit.blockX,
            hit.blockY,
            hit.blockZ,
            (byte) hit.sideHit,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord,
            C2SRtsInteractMessage.SOURCE_TOOL_SLOT,
            toolSlot,
            itemId != null ? itemId : "",
            origin.xCoord,
            origin.yCoord,
            origin.zCoord,
            dir.xCoord,
            dir.yCoord,
            dir.zCoord);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    /**
     * 问题15: 发送手持物品使用消息（食物、药水、工具等非方块物品）。
     */
    private boolean sendUseItemMessage(MovingObjectPosition hit) {
        byte toolSlot = (byte) state.interaction.selectedToolSlot;
        if (toolSlot < 0 || toolSlot >= 9) toolSlot = 0;
        com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUseItemMessage msg = new com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUseItemMessage(
            hit.blockX,
            hit.blockY,
            hit.blockZ,
            (byte) hit.sideHit,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord,
            toolSlot,
            state.interaction.selectedBlockId);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    // ── 旋转模式 ──

    private boolean handleRotateClick(MovingObjectPosition hit) {
        C2SRtsRotateBlockMessage msg = new C2SRtsRotateBlockMessage(hit.blockX, hit.blockY, hit.blockZ);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    // ── 存储链接模式 ──

    /**
     * Bug3修复：LINK_STORAGE 模式下的点击路由。
     * - 左键：检查目标方块是否已绑定 → 若是则取消绑定，若否则绑定为 NORMAL（可存可取）
     * - 右键：绑定为 EXTRACT_ONLY（仅提取不存入）
     */
    private boolean handleLinkStorageModeClick(int button, MovingObjectPosition hit) {
        if (button == 0) {
            // 左键：检查是否已绑定，是则取消，否则绑定
            BlockPos targetPos = new BlockPos(hit.blockX, hit.blockY, hit.blockZ);
            boolean alreadyLinked = false;
            for (BlockPos pos : state.storage.linkedStoragePositions) {
                if (pos.equals(targetPos)) {
                    alreadyLinked = true;
                    break;
                }
            }
            if (alreadyLinked) {
                // 已绑定 → 取消绑定
                C2SRtsUnlinkStorageMessage msg = new C2SRtsUnlinkStorageMessage(hit.blockX, hit.blockY, hit.blockZ);
                RtsNetworkManager.NETWORK.sendToServer(msg);
                return true;
            } else {
                // 未绑定 → 绑定为 NORMAL（可存可取）
                return handleLinkStorageClick(hit, C2SRtsLinkStorageMessage.MODE_NORMAL);
            }
        } else if (button == 1) {
            // 右键：绑定为 EXTRACT_ONLY（仅提取不存入）
            return handleLinkStorageClick(hit, C2SRtsLinkStorageMessage.MODE_EXTRACT_ONLY);
        }
        return false;
    }

    private boolean handleLinkStorageClick(MovingObjectPosition hit, byte linkMode) {
        C2SRtsLinkStorageMessage msg = new C2SRtsLinkStorageMessage(hit.blockX, hit.blockY, hit.blockZ, linkMode);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    // ── 辅助方法 ──

    private static ItemStack createBlockStack(String blockId, int meta) {
        Block block = Block.getBlockFromName(blockId);
        if (block == null) return null;
        ItemBlock itemBlock = (ItemBlock) net.minecraft.item.Item.getItemFromBlock(block);
        if (itemBlock == null) return null;
        return new ItemStack(itemBlock, 1, meta);
    }

    // ── P1-2: 实体射线检测 ──

    /**
     * 沿射线搜索最近的实体，返回 entityId。无命中返回 NO_ENTITY。
     */
    private int findEntityHit(Vec3 origin, Vec3 dir) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return C2SRtsInteractMessage.NO_ENTITY;

        Vec3 start = origin;
        Vec3 end = start.addVector(dir.xCoord * RAY_REACH, dir.yCoord * RAY_REACH, dir.zCoord * RAY_REACH);

        double closestDist = Double.MAX_VALUE;
        int closestEntityId = C2SRtsInteractMessage.NO_ENTITY;

        MovingObjectPosition blockHit = world.rayTraceBlocks(start, end);
        double blockDist = blockHit != null ? start.distanceTo(blockHit.hitVec) : RAY_REACH;

        boolean ctrlHeld = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL)
            || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL);

        // Ctrl 吸附：以射线命中点为中心 3x3x3 范围搜索实体
        if (ctrlHeld && blockHit != null) {
            double snapX = blockHit.hitVec.xCoord;
            double snapY = blockHit.hitVec.yCoord;
            double snapZ = blockHit.hitVec.zCoord;
            net.minecraft.util.AxisAlignedBB snapAABB = net.minecraft.util.AxisAlignedBB
                .getBoundingBox(snapX - 1.5D, snapY - 1.5D, snapZ - 1.5D, snapX + 1.5D, snapY + 1.5D, snapZ + 1.5D);

            for (Object obj : world.loadedEntityList) {
                if (!(obj instanceof net.minecraft.entity.Entity)) continue;
                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) obj;
                if (!entity.isEntityAlive()) continue;
                if (entity == mc.thePlayer) continue;

                if (entity.boundingBox.intersectsWith(snapAABB)) {
                    double dx = entity.posX - snapX;
                    double dy = entity.posY - snapY;
                    double dz = entity.posZ - snapZ;
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestEntityId = entity.getEntityId();
                    }
                }
            }
            return closestEntityId;
        }

        for (Object obj : world.loadedEntityList) {
            if (!(obj instanceof net.minecraft.entity.Entity)) continue;
            net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) obj;
            if (!entity.isEntityAlive()) continue;
            if (entity == mc.thePlayer) continue;

            float expand = entity.getCollisionBorderSize() + 1.0F;
            net.minecraft.util.AxisAlignedBB aabb = entity.boundingBox.expand(expand, expand, expand);
            MovingObjectPosition entityHit = aabb.calculateIntercept(start, end);
            if (entityHit != null) {
                double dist = start.distanceTo(entityHit.hitVec);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestEntityId = entity.getEntityId();
                }
            }
        }
        return closestEntityId;
    }

    /**
     * 发送带 entityId 的交互消息（实体交互优先）。
     */
    private boolean sendInteractMessageWithEntity(int entityId, MovingObjectPosition hit, Vec3 origin, Vec3 dir) {
        // Bug1修复: grace 期防闪烁
        com.rtsbuilding.rtsbuilding.ClientProxy.RtsKeyHandler.beginRemoteMenuOpenGrace();

        byte toolSlot = (byte) state.interaction.selectedToolSlot;
        if (toolSlot < 0 || toolSlot >= 9) toolSlot = 0;
        C2SRtsInteractMessage msg = new C2SRtsInteractMessage(
            entityId,
            hit.blockX,
            hit.blockY,
            hit.blockZ,
            (byte) hit.sideHit,
            hit.hitVec.xCoord,
            hit.hitVec.yCoord,
            hit.hitVec.zCoord,
            C2SRtsInteractMessage.SOURCE_TOOL_SLOT,
            toolSlot,
            "",
            origin.xCoord,
            origin.yCoord,
            origin.zCoord,
            dir.xCoord,
            dir.yCoord,
            dir.zCoord);
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    // ── Bug2修复：挖掘中止 ──

    /**
     * 发送挖掘中止消息（C2SRtsMineMessage start=false）。
     * 在左键释放时由 RtsScreen 调用，通知服务端 RtsMineManager 清除挖掘进度。
     *
     * 坐标传 0/0/0 因为服务端按 player UUID 查找活跃挖掘记录，
     * 不依赖坐标定位。
     */
    public void abortMine() {
        C2SRtsMineMessage msg = new C2SRtsMineMessage(
            0,
            0,
            0,
            (byte) 0,
            false, // start = false → abort
            (byte) 0,
            "",
            null,
            false);
        RtsNetworkManager.NETWORK.sendToServer(msg);
    }
}
