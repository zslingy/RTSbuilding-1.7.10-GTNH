package com.rtsbuilding.rtsbuilding.server.tracking;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.BlockEvent;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 方块追踪事件处理器 — 玩家放置/破坏方块时更新存储会话缓存。
 */
public final class RtsBlockTrackingEvents {

    private RtsBlockTrackingEvents() {}

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            RtsStorageSession session = RtsStorageManager.getSession((EntityPlayerMP) event.player);
            if (session.isAe2Linked()) {
                session.invalidateAe2Cache();
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
            RtsStorageSession session = RtsStorageManager.getSession(player);
            if (session.isAe2Linked()) {
                session.invalidateAe2Cache();
                RtsbuildingMod.LOGGER.debug(
                    "RtsBlockTracking: invalidated AE2 cache for {} after break at ({}, {}, {})",
                    event.getPlayer()
                        .getDisplayName(),
                    event.x,
                    event.y,
                    event.z);
            }
            // Bug8修复：方块破坏时清理存储绑定（对齐原版 onLinkedStorageBlockBroken 调用）
            RtsStorageManager.onLinkedStorageBlockBroken(event.world, event.x, event.y, event.z);
        }
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new RtsBlockTrackingEvents());
        RtsbuildingMod.LOGGER.info("RTS Block Tracking Events registered");
    }
}
