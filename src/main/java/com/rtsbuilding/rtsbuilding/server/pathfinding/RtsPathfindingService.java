package com.rtsbuilding.rtsbuilding.server.pathfinding;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.pathfinding.S2CRtsPathfindingUpdateMessage;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class RtsPathfindingService {

    private static final Map<UUID, ActivePath> ACTIVE_PATHS = new ConcurrentHashMap<>();

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new TickHandler());
    }

    public static void startPathfinding(EntityPlayerMP player, double x, double y, double z, String mode) {
        if (!RtsCameraManager.isActive(player)) return;

        ActivePath existing = ACTIVE_PATHS.get(player.getUniqueID());
        if (existing != null) {
            player.motionX = 0;
            player.motionY = 0;
            player.motionZ = 0;
        }

        if (mode.equals("teleport")) {
            player.setPositionAndUpdate(x, y, z);
            RtsNetworkManager.NETWORK.sendTo(new S2CRtsPathfindingUpdateMessage(x, y, z, true), player);
            return;
        }

        double speed = mode.equals("sprint") ? 0.25 : mode.equals("fly") ? 0.3 : 0.2;
        double dx = x - player.posX;
        double dy = y - player.posY;
        double dz = z - player.posZ;
        double dist = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));

        player.motionX = dx / dist * speed;
        player.motionZ = dz / dist * speed;
        if (mode.equals("fly")) {
            player.motionY = dy / dist * speed;
        }
        player.velocityChanged = true;

        ACTIVE_PATHS.put(player.getUniqueID(), new ActivePath(x, y, z, 0, mode));

        RtsNetworkManager.NETWORK.sendTo(new S2CRtsPathfindingUpdateMessage(x, y, z, false), player);
    }

    public static void tickAll() {
        if (ACTIVE_PATHS.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActivePath>> it = ACTIVE_PATHS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActivePath> entry = it.next();
            ActivePath path = entry.getValue();
            EntityPlayerMP player = getPlayerByUuid(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            path.ticksElapsed++;
            if (path.ticksElapsed > 300) {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
                player.velocityChanged = true;
                RtsNetworkManager.NETWORK
                    .sendTo(new S2CRtsPathfindingUpdateMessage(path.targetX, path.targetY, path.targetZ, true), player);
                it.remove();
                continue;
            }

            double dx = player.posX - path.targetX;
            double dy = player.posY - path.targetY;
            double dz = player.posZ - path.targetZ;
            if (dx * dx + dy * dy + dz * dz < 1.5) {
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
                player.velocityChanged = true;
                RtsNetworkManager.NETWORK
                    .sendTo(new S2CRtsPathfindingUpdateMessage(path.targetX, path.targetY, path.targetZ, true), player);
                it.remove();
                continue;
            }

            double ndx = path.targetX - player.posX;
            double ndy = path.targetY - player.posY;
            double ndz = path.targetZ - player.posZ;
            double dist = Math.max(0.1, Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz));
            double speed = path.mode.equals("sprint") ? 0.25 : path.mode.equals("fly") ? 0.3 : 0.2;

            player.motionX = ndx / dist * speed;
            player.motionZ = ndz / dist * speed;
            if (path.mode.equals("fly")) {
                player.motionY = ndy / dist * speed;
            }
            player.velocityChanged = true;
        }
    }

    private static EntityPlayerMP getPlayerByUuid(UUID uuid) {
        return MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(uuid.toString());
    }

    public static class TickHandler {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickAll();
            }
        }
    }

    private static class ActivePath {

        final double targetX;
        final double targetY;
        final double targetZ;
        int ticksElapsed;
        final String mode;

        ActivePath(double targetX, double targetY, double targetZ, int ticksElapsed, String mode) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.ticksElapsed = ticksElapsed;
            this.mode = mode;
        }
    }
}
