package com.rtsbuilding.rtsbuilding.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMineMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsRotateBlockMessage;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltimineMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStorageMessage;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelMessage;
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
        // Bug2修复：连锁挖掘激活时发送 C2SRtsUltimineMessage 而非 C2SRtsMineMessage
        if (state.interaction.ultimineActive) {
            String toolItemId = state.interaction.selectedBlockId != null ? state.interaction.selectedBlockId : "";
            C2SRtsUltimineMessage msg = new C2SRtsUltimineMessage(
                hit.blockX,
                hit.blockY,
                hit.blockZ,
                (byte) hit.sideHit,
                (byte) 0,
                toolItemId,
                null,
                (short) state.interaction.ultimineLimit,
                (byte) 0); // mode 0 = chain
            RtsNetworkManager.NETWORK.sendToServer(msg);
            return true;
        }
        // Bug2修复：发送 C2SRtsMineMessage(start=true) 替代即时 C2SRtsBreakMessage
        // 服务端 RtsMineManager 将逐 tick 累积挖掘进度
        String toolItemId = state.interaction.selectedBlockId != null ? state.interaction.selectedBlockId : "";
        C2SRtsMineMessage msg = new C2SRtsMineMessage(
            hit.blockX,
            hit.blockY,
            hit.blockZ,
            (byte) hit.sideHit,
            true, // start = true
            (byte) 0, // toolSlot (0 = 当前选中)
            toolItemId,
            null, // toolPrototype (让服务端根据 toolSlot 自行获取)
            false); // allowPlacedBlockRecovery
        RtsNetworkManager.NETWORK.sendToServer(msg);
        return true;
    }

    // ── 右键：放置 or 交互 ──

    private boolean handlePlaceOrInteract(MovingObjectPosition hit, Vec3 origin, Vec3 dir, BuilderMode mode) {
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
     * 交互模式右键：
     * - 有选中物品 → 放置方块
     * - 空手 → 交互（打开方块GUI / 实体交互）
     * - 有选中流体 → 流体放置（TODO）
     */
    private boolean handleInteractRightClick(MovingObjectPosition hit, Vec3 origin, Vec3 dir) {
        String blockId = state.interaction.selectedBlockId;
        int meta = state.interaction.selectedBlockMeta;

        // 没有选中物品 → 空手交互
        if (blockId == null || blockId.isEmpty() || "minecraft:air".equals(blockId)) {
            return sendInteractMessage(hit, origin, dir);
        }

        // 有选中物品 → 放置
        ItemStack prototype = createBlockStack(blockId, meta);
        if (prototype == null) {
            // 无法解析为方块 → 降级为交互
            return sendInteractMessage(hit, origin, dir);
        }

        return sendPlaceMessage(hit, origin, dir, blockId, prototype);
    }

    private boolean sendPlaceMessage(MovingObjectPosition hit, Vec3 origin, Vec3 dir, String blockId,
        ItemStack prototype) {
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

    private boolean sendInteractMessage(MovingObjectPosition hit, Vec3 origin, Vec3 dir) {
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
            (byte) 0,
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
