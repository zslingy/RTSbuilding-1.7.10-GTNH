package com.rtsbuilding.rtsbuilding.server.service;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsPlayerUtil;

public class RtsSessionService {

    private static final String NBT_STORAGE_ROOT = "rtsbuilding_storage";

    public static RtsStorageSession getSession(EntityPlayerMP player) {
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageSession session = RtsStorageManager.getSessions()
            .get(id);
        if (session == null) {
            session = new RtsStorageSession();
            RtsStorageManager.getSessions()
                .put(id, session);
        }
        return session;
    }

    public static void removeSession(EntityPlayerMP player) {
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageManager.getSessions()
            .remove(id);
    }

    public static void saveSessionNBT(EntityPlayerMP player) {
        if (player == null) return;
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageSession session = RtsStorageManager.getSessions()
            .get(id);
        if (session == null) return;
        NBTTagCompound root = new NBTTagCompound();
        session.writeToNBT(root);
        player.getEntityData()
            .setTag(NBT_STORAGE_ROOT, root);
    }

    public static void loadSessionNBT(EntityPlayerMP player) {
        if (player == null) return;
        UUID id = RtsPlayerUtil.getUUID(player);
        RtsStorageSession session = RtsStorageManager.getSessions()
            .get(id);
        if (session == null) return;
        NBTTagCompound persistent = player.getEntityData();
        if (persistent.hasKey(NBT_STORAGE_ROOT, 10)) {
            session.readFromNBT(persistent.getCompoundTag(NBT_STORAGE_ROOT));
        }
    }

    public static void clearAll() {
        RtsStorageManager.getSessions()
            .clear();
    }
}
