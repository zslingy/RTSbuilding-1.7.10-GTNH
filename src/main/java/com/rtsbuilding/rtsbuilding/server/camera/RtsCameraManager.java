package com.rtsbuilding.rtsbuilding.server.camera;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStateMessage;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.util.RtsPlayerUtil;

/**
 * RTS 相机管理器（服务端）— 管理所有玩家的 RTS 俯视相机生命周期。
 *
 * 完整迁移自原版 RtsCameraManager（NeoForge 1.21），适配 1.7.10：
 * - Session 替代 Java 14+ record
 * - MathHelper.clamp_float/clamp_double 替代 Mth.clamp
 * - EntityPlayerMP 替代 ServerPlayer
 * - posX/posY/posZ 替代 Vec3
 * - 集成进度系统门控（阶段C）
 */
public final class RtsCameraManager {

    // ---- 边界常量 ----
    private static final double MIN_HEIGHT = -35.0D;
    private static final double MAX_HEIGHT = 110.0D;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;

    // ---- 旋转常量 ----
    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;

    // ---- 移动速度 ----
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;

    // ---- 家园选择半径 ----
    public static final double HOME_SELECTION_RADIUS_BLOCKS = 34.0D;

    // ---- 会话存储 ----
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RtsCameraManager() {}

    // ========================================================================
    // 公开 API：RTS 相机切换（集成进度门控）
    // ========================================================================

    /**
     * 为玩家启用 RTS 俯视相机。
     * 检查进度系统：未解锁则提示，无家园则进入家园选择流程。
     *
     * @param startAtPlayerHead true=从玩家头部高度开始，false=默认18格高度
     */
    public static void enableCamera(EntityPlayerMP player, boolean startAtPlayerHead) {
        // ---- 进度门控 ----
        if (!RtsProgressionManager.canUse(player, RtsFeature.CAMERA)) {
            player.addChatMessage(new ChatComponentText("RTS camera is not unlocked."));
            return;
        }
        if (RtsProgressionManager.shouldStartHomeSelection(player)) {
            startHomeSelection(player, startAtPlayerHead);
            return;
        }
        if (!RtsProgressionManager.canStartNormalRts(player)) {
            player.addChatMessage(new ChatComponentText("Set an RTS home first."));
            return;
        }
        startNormal(player, startAtPlayerHead);
    }

    /**
     * 普通 RTS 相机启动（已有家园时调用）。
     */
    private static void startNormal(EntityPlayerMP player, boolean startAtPlayerHead) {
        UUID playerId = RtsPlayerUtil.getUUID(player);
        disableCamera(player);

        cleanupOrphanCameras(player.mcServer);
        discardOwnedCameras(player, null);

        WorldServer world = player.getServerForPlayer();

        double anchorX = Math.floor(player.posX) + 0.5D;
        double anchorY = player.posY;
        double anchorZ = Math.floor(player.posZ) + 0.5D;

        float yaw = snapQuarter(player.rotationYaw);
        float pitch = 70.0F;
        double cameraY = startAtPlayerHead ? player.posY + player.getEyeHeight() + 4.0D : anchorY + 18.0D;
        double maxRadius = RtsProgressionManager.isEnabled() ? RtsProgressionManager.getActionRadius(player) : 128.0D;

        RtsCameraEntity camera = new RtsCameraEntity(world);
        camera.setOwnerUuid(playerId);
        camera.snapTo(anchorX, cameraY, anchorZ, yaw, pitch);
        world.spawnEntityInWorld(camera);

        Session session = new Session(
            camera.getUniqueID(),
            anchorX,
            anchorY,
            anchorZ,
            anchorX,
            cameraY,
            anchorZ,
            yaw,
            pitch,
            cameraY - anchorY,
            false,
            maxRadius,
            startAtPlayerHead);

        SESSIONS.put(playerId, session);

        S2CRtsCameraStateMessage msg = new S2CRtsCameraStateMessage(
            true,
            camera.getEntityId(),
            anchorX,
            anchorY,
            anchorZ,
            maxRadius,
            session.heightOffset(),
            session.yawDeg(),
            session.pitchDeg(),
            false,
            session.closeRangeAllowed());
        RtsNetworkManager.NETWORK.sendTo(msg, player);
    }

    // ========================================================================
    // 家园选择模式
    // ========================================================================

    /**
     * 家园选择模式相机启动。
     * 相机锚定在玩家位置，使用固定小范围边界（34 格），
     * 引导玩家在附近选择家园位置。
     */
    private static void startHomeSelection(EntityPlayerMP player, boolean closeRangeAllowed) {
        UUID playerId = RtsPlayerUtil.getUUID(player);
        disableCamera(player);

        cleanupOrphanCameras(player.mcServer);
        discardOwnedCameras(player, null);

        WorldServer world = player.getServerForPlayer();

        double anchorX = Math.floor(player.posX) + 0.5D;
        double anchorY = player.posY;
        double anchorZ = Math.floor(player.posZ) + 0.5D;

        float yaw = snapQuarter(player.rotationYaw);
        float pitch = 70.0F;
        double cameraY = anchorY + 18.0D;
        double maxRadius = HOME_SELECTION_RADIUS_BLOCKS;

        RtsCameraEntity camera = new RtsCameraEntity(world);
        camera.setOwnerUuid(playerId);
        camera.snapTo(anchorX, cameraY, anchorZ, yaw, pitch);
        world.spawnEntityInWorld(camera);

        Session session = new Session(
            camera.getUniqueID(),
            anchorX,
            anchorY,
            anchorZ,
            anchorX,
            cameraY,
            anchorZ,
            yaw,
            pitch,
            cameraY - anchorY,
            true,
            maxRadius,
            closeRangeAllowed);

        SESSIONS.put(playerId, session);

        S2CRtsCameraStateMessage msg = new S2CRtsCameraStateMessage(
            true,
            camera.getEntityId(),
            anchorX,
            anchorY,
            anchorZ,
            maxRadius,
            session.heightOffset(),
            session.yawDeg(),
            session.pitchDeg(),
            true,
            closeRangeAllowed);
        RtsNetworkManager.NETWORK.sendTo(msg, player);
    }

    /**
     * 从进度面板触发家园选择。
     * 先调用进度系统 beginHomeSelection，再启动家园选择模式相机。
     */
    public static void startHomeSelectionFromPanel(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled()) {
            return;
        }
        RtsProgressionManager.beginHomeSelection(player);
        startHomeSelection(player, false);
    }

    /**
     * 提交家园后，从家园选择模式恢复为普通 RTS 相机。
     */
    public static void restartNormalFromHomeSelection(EntityPlayerMP player) {
        Session session = SESSIONS.get(RtsPlayerUtil.getUUID(player));
        if (session == null || !session.homeSelection()) {
            return;
        }
        Entity entity = findCameraEntity(player.mcServer, session.cameraUuid());
        if (entity != null) {
            entity.setDead();
        }
        SESSIONS.remove(RtsPlayerUtil.getUUID(player));
        startNormal(player, session.closeRangeAllowed());
    }

    /**
     * 禁用玩家的 RTS 俯视相机。
     */
    public static void disableCamera(EntityPlayerMP player) {
        UUID playerId = RtsPlayerUtil.getUUID(player);
        Session session = SESSIONS.remove(playerId);

        if (session != null) {
            Entity entity = findCameraEntity(player.mcServer, session.cameraUuid());
            if (entity != null) {
                entity.setDead();
            }
        }

        discardOwnedCameras(player, null);

        S2CRtsCameraStateMessage msg = new S2CRtsCameraStateMessage(
            false,
            -1,
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            18.0D,
            0.0F,
            70.0F,
            false,
            false);
        RtsNetworkManager.NETWORK.sendTo(msg, player);
    }

    public static boolean isCameraActive(UUID playerId) {
        return SESSIONS.containsKey(playerId);
    }

    /**
     * 相机会话是否活跃（按玩家实体）。
     */
    public static boolean isActive(EntityPlayerMP player) {
        return SESSIONS.containsKey(RtsPlayerUtil.getUUID(player));
    }

    // ========================================================================
    // 核心：相机移动
    // ========================================================================

    /**
     * 处理完整的相机移动。
     *
     * 1:1 迁移自原版 RtsCameraManager.move()：
     * - 10 个输入字段
     * - 旋转 clamping（±20°）、增益计算（ROTATE_GAIN_X/Y）
     * - quarter-snap（rotateSteps）
     * - 速度计算：normal=0.45, fast=0.80
     * - 鼠标拖拽平移（dragScale）
     * - 滚轮沿视线 dolly 缩放（DOLLY_PER_SCROLL=2.6）
     * - 锚点边界 XZ 正方形 clamping + Y 范围 [-35, +110]
     * - Session 持久化更新
     */
    public static void move(EntityPlayerMP player, float forward, float strafe, float vertical, float panX, float panY,
        float rotateX, float rotateY, float scroll, int rotateSteps, boolean fast) {

        Session session = SESSIONS.get(RtsPlayerUtil.getUUID(player));
        if (session == null) {
            return;
        }

        RtsCameraEntity camera = getOrRestoreCamera(player, session);

        // ---- 旋转 clamping + 增益 ----
        float safeRotateX = MathHelper.clamp_float(rotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float safeRotateY = MathHelper.clamp_float(rotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        float yaw = session.yawDeg() + (safeRotateX * ROTATE_GAIN_X);
        if (rotateSteps != 0) {
            yaw = snapQuarter(yaw + (90.0F * rotateSteps));
        }

        float pitch = MathHelper.clamp_float(session.pitchDeg() + (safeRotateY * ROTATE_GAIN_Y), MIN_PITCH, MAX_PITCH);

        // ---- 速度计算 ----
        double speed = fast ? 0.80D : 0.45D;

        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetX = camera.posX;
        double targetY = camera.posY;
        double targetZ = camera.posZ;

        // ---- WASD 移动 (与原版 ClientRtsController.applyLocalPrediction 对齐) ----
        float safeVertical = MathHelper.clamp_float(vertical, -1.0F, 1.0F);
        double dx = (-sin * forward + cos * strafe) * speed;
        double dz = (cos * forward + sin * strafe) * speed;

        // ---- 鼠标拖拽平移 ----
        double dragScale = 0.020D * Math.max(8.0D, session.heightOffset());
        double moveRight = panX * dragScale;
        double moveForwardPan = -panY * dragScale;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        dx += rightX * moveRight + fwdX * moveForwardPan;
        dz += rightZ * moveRight + fwdZ * moveForwardPan;

        targetX += dx;
        targetY += safeVertical * (fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        targetZ += dz;

        // ---- 滚轮沿视线 dolly 缩放 ----
        if (scroll != 0.0F) {
            double pitchRad = Math.toRadians(pitch);
            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double dolly = scroll * DOLLY_PER_SCROLL;
            targetX += lookX * dolly;
            targetY += lookY * dolly;
            targetZ += lookZ * dolly;
        }

        // ---- 边界 clamping ----
        double halfExtent = actionHalfExtent(player, session);
        targetX = MathHelper.clamp_double(targetX, session.anchorX() - halfExtent, session.anchorX() + halfExtent);
        targetZ = MathHelper.clamp_double(targetZ, session.anchorZ() - halfExtent, session.anchorZ() + halfExtent);
        targetY = MathHelper.clamp_double(targetY, session.anchorY() + MIN_HEIGHT, session.anchorY() + MAX_HEIGHT);

        // ---- 应用位置 ----
        camera.snapTo(targetX, targetY, targetZ, yaw, pitch);

        // ---- 更新 Session ----
        SESSIONS.put(
            RtsPlayerUtil.getUUID(player),
            new Session(
                camera.getUniqueID(),
                session.anchorX(),
                session.anchorY(),
                session.anchorZ(),
                targetX,
                targetY,
                targetZ,
                yaw,
                pitch,
                targetY - session.anchorY(),
                session.homeSelection(),
                session.maxRadius(),
                session.closeRangeAllowed()));
    }

    // ========================================================================
    // 相机恢复
    // ========================================================================

    /**
     * 获取或恢复相机实体。
     * 如果原相机实体因区块卸载等原因消失，重新创建。
     */
    private static RtsCameraEntity getOrRestoreCamera(EntityPlayerMP player, Session session) {
        Entity baseEntity = findCameraEntity(player.mcServer, session.cameraUuid());
        if (baseEntity instanceof RtsCameraEntity && baseEntity.worldObj == player.worldObj) {
            RtsCameraEntity camera = (RtsCameraEntity) baseEntity;
            if (camera.getOwnerUuid() == null) {
                camera.setOwnerUuid(RtsPlayerUtil.getUUID(player));
            }
            if (!RtsPlayerUtil.getUUID(player)
                .equals(camera.getOwnerUuid())) {
                camera.setDead();
            } else {
                return camera;
            }
        }

        if (baseEntity != null) {
            baseEntity.setDead();
        }

        // 重建相机实体
        WorldServer world = player.getServerForPlayer();
        RtsCameraEntity restored = new RtsCameraEntity(world);
        restored.setOwnerUuid(RtsPlayerUtil.getUUID(player));
        restored.snapTo(
            session.cameraPosX(),
            session.cameraPosY(),
            session.cameraPosZ(),
            session.yawDeg(),
            session.pitchDeg());
        world.spawnEntityInWorld(restored);

        SESSIONS.put(
            RtsPlayerUtil.getUUID(player),
            new Session(
                restored.getUniqueID(),
                session.anchorX(),
                session.anchorY(),
                session.anchorZ(),
                session.cameraPosX(),
                session.cameraPosY(),
                session.cameraPosZ(),
                session.yawDeg(),
                session.pitchDeg(),
                session.heightOffset(),
                session.homeSelection(),
                session.maxRadius(),
                session.closeRangeAllowed()));

        // 通知客户端新实体 ID
        S2CRtsCameraStateMessage msg = new S2CRtsCameraStateMessage(
            true,
            restored.getEntityId(),
            session.anchorX(),
            session.anchorY(),
            session.anchorZ(),
            maxRadius(player, session),
            session.heightOffset(),
            session.yawDeg(),
            session.pitchDeg(),
            session.homeSelection(),
            session.closeRangeAllowed());
        RtsNetworkManager.NETWORK.sendTo(msg, player);

        return restored;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 清理所有维度中的孤立相机实体（Session 中不存在的）。
     */
    @SuppressWarnings("unchecked")
    private static void cleanupOrphanCameras(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (WorldServer world : server.worldServers) {
            for (Entity entity : (Iterable<Entity>) world.loadedEntityList) {
                if (entity instanceof RtsCameraEntity && !isActiveCamera(entity.getUniqueID())) {
                    entity.setDead();
                }
            }
        }
    }

    /**
     * 丢弃指定玩家拥有的所有相机实体（可选保留指定 UUID）。
     */
    @SuppressWarnings("unchecked")
    private static void discardOwnedCameras(EntityPlayerMP player, UUID keepUuid) {
        if (player == null || player.mcServer == null) return;
        UUID ownerUuid = RtsPlayerUtil.getUUID(player);
        for (WorldServer world : player.mcServer.worldServers) {
            for (Object obj : world.loadedEntityList) {
                Entity entity = (Entity) obj;
                if (entity instanceof RtsCameraEntity) {
                    RtsCameraEntity camera = (RtsCameraEntity) entity;
                    if (ownerUuid.equals(camera.getOwnerUuid()) && !camera.getUniqueID()
                        .equals(keepUuid)) {
                        camera.setDead();
                    }
                }
            }
        }
    }

    /**
     * 检查相机 UUID 是否属于活跃 Session。
     */
    private static boolean isActiveCamera(UUID cameraUuid) {
        if (cameraUuid == null) return false;
        for (Session session : SESSIONS.values()) {
            if (cameraUuid.equals(session.cameraUuid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在服务器所有维度中查找指定 UUID 的实体。
     */
    private static Entity findCameraEntity(net.minecraft.server.MinecraftServer server, UUID cameraUuid) {
        if (server == null || cameraUuid == null) return null;
        for (WorldServer world : server.worldServers) {
            Entity entity = findEntityByUUID(world, cameraUuid);
            if (entity != null) return entity;
        }
        return null;
    }

    /**
     * 在单个世界中按 UUID 查找实体（1.7.10 没有 getEntity(UUID)，需遍历 loadedEntityList）。
     */
    @SuppressWarnings("unchecked")
    private static Entity findEntityByUUID(WorldServer world, UUID uuid) {
        for (Entity entity : (Iterable<Entity>) world.loadedEntityList) {
            if (uuid.equals(entity.getUniqueID())) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 计算操作半径（集成进度系统）。
     */
    private static double maxRadius(EntityPlayerMP player, Session session) {
        if (session.homeSelection()) {
            return session.maxRadius();
        }
        return RtsProgressionManager.isEnabled() ? RtsProgressionManager.getActionRadius(player)
            : RtsProgressionManager.DEFAULT_MAX_ACTION_RADIUS_BLOCKS;
    }

    /**
     * 计算操作半范围（正方形边界半边）。
     */
    private static double actionHalfExtent(EntityPlayerMP player, Session session) {
        return maxRadius(player, session);
    }

    /**
     * Quarter-snap：将 yaw 对齐到最近的 90° 倍数。
     */
    private static float snapQuarter(float yaw) {
        int quarter = Math.round(yaw / 90.0F);
        return quarter * 90.0F;
    }

    /**
     * 玩家离开时清理。
     */
    public static void cleanupPlayer(EntityPlayerMP player) {
        UUID playerId = RtsPlayerUtil.getUUID(player);
        Session session = SESSIONS.remove(playerId);
        if (session != null) {
            Entity entity = findCameraEntity(player.mcServer, session.cameraUuid());
            if (entity != null) {
                entity.setDead();
            }
        }
        discardOwnedCameras(player, null);
    }

    /**
     * 获取相机位置（供建造等系统使用）。
     */
    public static double[] getCameraPosition(EntityPlayerMP player) {
        Session session = SESSIONS.get(RtsPlayerUtil.getUUID(player));
        if (session != null) {
            return new double[] { session.cameraPosX(), session.cameraPosY(), session.cameraPosZ() };
        }
        return null;
    }

    /**
     * 检查方块位置是否在操作范围内。
     */
    public static boolean isWithinActionRange(EntityPlayerMP player, int blockX, int blockY, int blockZ) {
        Session session = SESSIONS.get(RtsPlayerUtil.getUUID(player));
        if (session == null || session.homeSelection()) return false;

        double dx = (blockX + 0.5D) - session.anchorX();
        double dz = (blockZ + 0.5D) - session.anchorZ();
        double halfExtent = actionHalfExtent(player, session);
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }
}
