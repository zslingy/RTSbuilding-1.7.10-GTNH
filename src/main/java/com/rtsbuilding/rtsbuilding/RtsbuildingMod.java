package com.rtsbuilding.rtsbuilding;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = RtsbuildingMod.MODID,
    name = RtsbuildingMod.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class RtsbuildingMod {

    public static final String MODID = "rtsbuilding";
    public static final String MODNAME = "RTS Building Build From Above";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Instance(MODID)
    public static RtsbuildingMod instance;

    @SidedProxy(
        clientSide = "com.rtsbuilding.rtsbuilding.ClientProxy",
        serverSide = "com.rtsbuilding.rtsbuilding.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
