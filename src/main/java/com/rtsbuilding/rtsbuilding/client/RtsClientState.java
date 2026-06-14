package com.rtsbuilding.rtsbuilding.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

/**
 * 集中式客户端状态 — 聚合所有 ViewModel。
 * 
 * 替代原 ClientRtsController 3155 行怪兽，拆分为 5 个专注的 ViewModel。
 * 单例模式，提供一个统一的客户端状态访问入口。
 */
public class RtsClientState {

    // ---- 核心 ViewModel ----
    public final CameraViewModel camera;
    public final StorageViewModel storage;
    public final CraftViewModel craft;
    public final ProgressionViewModel progression;
    public final InteractionViewModel interaction;

    // ---- UI缩放 ----
    public float uiZoom = 1.0f;

    /** Bug9修复：设置面板是否作为子画面打开 */
    public boolean settingsScreenOpen = false;

    // ---- 单例 ----
    private static RtsClientState INSTANCE;

    private RtsClientState() {
        camera = new CameraViewModel();
        storage = new StorageViewModel();
        craft = new CraftViewModel();
        progression = new ProgressionViewModel();
        interaction = new InteractionViewModel();
    }

    /** 获取全局单例实例 */
    public static RtsClientState get() {
        if (INSTANCE == null) {
            INSTANCE = new RtsClientState();
        }
        return INSTANCE;
    }

    /** 重置为新会话的所有状态 */
    public void resetForNewSession() {
        camera.resetForNewSession();
        storage.resetForNewSession();
        craft.resetForNewSession();
        progression.resetForNewSession();
        interaction.resetForNewSession();
    }

    /** 每帧开始时重置所有 ViewModel */
    public void resetAllFrameStates() {
        camera.resetFrameState();
        storage.resetFrameState();
        craft.resetFrameState();
        progression.resetFrameState();
        interaction.resetFrameState();
    }

    /** 持久化客户端 UI 状态到 NBT 文件 */
    public void persist() {
        NBTTagCompound tag = new NBTTagCompound();

        // 相机状态 (inputSensitivityIndex + sensitivity + searchBoxFocused)
        NBTTagCompound cam = new NBTTagCompound();
        cam.setInteger("inputSensitivityIndex", camera.inputSensitivityIndex);
        cam.setFloat("sensitivity", camera.sensitivity);
        cam.setBoolean("searchBoxFocused", camera.searchBoxFocused);
        tag.setTag("camera", cam);

        // 交互状态 (包括设置项 toggle)
        NBTTagCompound inter = new NBTTagCompound();
        inter.setString("quickBuildShape", interaction.quickBuildShape);
        inter.setString("quickBuildFill", interaction.quickBuildFill);
        inter.setInteger("quickBuildSizeX", interaction.quickBuildSizeX);
        inter.setInteger("quickBuildSizeY", interaction.quickBuildSizeY);
        inter.setInteger("quickBuildSizeZ", interaction.quickBuildSizeZ);
        inter.setInteger("quickBuildRotation", interaction.quickBuildRotation);
        inter.setBoolean("autoStoreMinedDrops", interaction.autoStoreMinedDrops);
        inter.setBoolean("startCameraAtPlayerHead", interaction.startCameraAtPlayerHead);
        inter.setBoolean("allowPlacedBlockRecovery", interaction.allowPlacedBlockRecovery);
        inter.setBoolean("debugButtonVisible", interaction.debugButtonVisible);
        inter.setBoolean("containerOverlayEnabled", interaction.containerOverlayEnabled);
        inter.setBoolean("shiftImportEnabled", interaction.shiftImportEnabled);
        inter.setBoolean("invertPanDragX", interaction.invertPanDragX);
        inter.setBoolean("invertPanDragY", interaction.invertPanDragY);
        inter.setBoolean("smoothCamera", interaction.smoothCamera);
        inter.setBoolean("damageSoundEnabled", interaction.damageSoundEnabled);
        inter.setBoolean("damageAutoReturnEnabled", interaction.damageAutoReturnEnabled);
        inter.setBoolean("bdNetworkEnabled", interaction.bdNetworkEnabled);
        tag.setTag("interaction", inter);

        // UI 缩放
        tag.setFloat("uiZoom", uiZoom);

        // 写入文件
        File file = getStateFile();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            CompressedStreamTools.write(tag, out);
        } catch (IOException e) {
            RtsbuildingMod.LOGGER.error("Failed to persist RTS client state", e);
        }
    }

    /** 从 NBT 文件恢复客户端 UI 状态 */
    public void restore() {
        File file = getStateFile();
        if (!file.exists()) return;

        NBTTagCompound tag;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            tag = CompressedStreamTools.read(in);
        } catch (IOException e) {
            RtsbuildingMod.LOGGER.error("Failed to restore RTS client state", e);
            return;
        }

        // 相机状态
        if (tag.hasKey("camera")) {
            NBTTagCompound cam = tag.getCompoundTag("camera");
            camera.inputSensitivityIndex = cam.getInteger("inputSensitivityIndex");
            camera.sensitivity = cam.getFloat("sensitivity");
            camera.searchBoxFocused = cam.getBoolean("searchBoxFocused");
        }

        // 交互状态
        if (tag.hasKey("interaction")) {
            NBTTagCompound inter = tag.getCompoundTag("interaction");
            interaction.quickBuildShape = inter.getString("quickBuildShape");
            interaction.quickBuildFill = inter.getString("quickBuildFill");
            interaction.quickBuildSizeX = inter.getInteger("quickBuildSizeX");
            interaction.quickBuildSizeY = inter.getInteger("quickBuildSizeY");
            interaction.quickBuildSizeZ = inter.getInteger("quickBuildSizeZ");
            interaction.quickBuildRotation = inter.getInteger("quickBuildRotation");
            interaction.autoStoreMinedDrops = safeBoolean(inter, "autoStoreMinedDrops", true);
            interaction.startCameraAtPlayerHead = safeBoolean(inter, "startCameraAtPlayerHead", false);
            interaction.allowPlacedBlockRecovery = safeBoolean(inter, "allowPlacedBlockRecovery", false);
            interaction.debugButtonVisible = safeBoolean(inter, "debugButtonVisible", false);
            interaction.containerOverlayEnabled = safeBoolean(inter, "containerOverlayEnabled", true);
            interaction.shiftImportEnabled = safeBoolean(inter, "shiftImportEnabled", true);
            interaction.invertPanDragX = safeBoolean(inter, "invertPanDragX", false);
            interaction.invertPanDragY = safeBoolean(inter, "invertPanDragY", false);
            interaction.smoothCamera = safeBoolean(inter, "smoothCamera", true);
            interaction.damageSoundEnabled = safeBoolean(inter, "damageSoundEnabled", true);
            interaction.damageAutoReturnEnabled = safeBoolean(inter, "damageAutoReturnEnabled", true);
            interaction.bdNetworkEnabled = safeBoolean(inter, "bdNetworkEnabled", false);
        }

        uiZoom = tag.getFloat("uiZoom");
    }

    private static File getStateFile() {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "rts_client_state.dat");
    }

    private static boolean safeBoolean(NBTTagCompound tag, String key, boolean def) {
        return tag.hasKey(key) ? tag.getBoolean(key) : def;
    }
}
