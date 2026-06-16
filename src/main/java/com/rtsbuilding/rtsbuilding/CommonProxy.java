package com.rtsbuilding.rtsbuilding;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.server.menu.RtsGuiHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RecipeScanCache;
import com.rtsbuilding.rtsbuilding.server.tracking.RtsBlockTrackingEvents;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.EntityRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        // ---- Network Registration (stage 2) ----
        RtsNetworkManager.registerMessages();

        // ---- Entity Registration ----
        int entityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(RtsCameraEntity.class, "rts_camera", entityId);
        EntityRegistry
            .registerModEntity(RtsCameraEntity.class, "rts_camera", entityId, RtsbuildingMod.instance, 128, 1, false);

        RtsbuildingMod.LOGGER.info("RTSBuilding preInit complete. Version: " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        // 注册方块追踪事件
        RtsBlockTrackingEvents.register();

        // ── Bug2修复：注册渐进式挖掘管理器（ServerTickEvent 推进挖掘进度） ──
        com.rtsbuilding.rtsbuilding.server.RtsMineManager.register();

        // ── 注册存储管理器 tick（驱动漏斗拾取和 AE2 定期刷新） ──
        com.rtsbuilding.rtsbuilding.server.RtsStorageManager.register();

        // ── Bug5修复：注册蓝图放置服务（ServerTickEvent 推进蓝图放置） ──
        FMLCommonHandler.instance()
            .bus()
            .register(com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService.create());

        // ── Bug10修复：注册玩家登出/登录事件处理器（清理残留会话） ──
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        // ── 注册 GUI Handler ──
        NetworkRegistry.INSTANCE.registerGuiHandler(RtsbuildingMod.instance, new RtsGuiHandler());
        RtsbuildingMod.LOGGER.info("CommonProxy: Registered RtsGuiHandler for craft terminal GUI");
    }

    public void postInit(FMLPostInitializationEvent event) {}

    /** 阶段7: 服务端启动时标记配方缓存失效（缓存惰性重建，首次请求触发） */
    public void serverStarting(FMLServerStartingEvent event) {
        RecipeScanCache.markDirty();
    }

    // ── Bug10修复：玩家登出/登录事件处理器 ──

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager.disableCamera(player);
            com.rtsbuilding.rtsbuilding.server.RtsStorageManager.saveSessionNBT(player);
            com.rtsbuilding.rtsbuilding.server.RtsStorageManager.removeSession(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager.disableCamera(player);
            com.rtsbuilding.rtsbuilding.server.RtsStorageManager.removeSession(player);
            com.rtsbuilding.rtsbuilding.server.RtsStorageManager.loadSessionNBT(player);
        }
    }
}
