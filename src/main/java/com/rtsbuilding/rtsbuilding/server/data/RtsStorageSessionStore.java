package com.rtsbuilding.rtsbuilding.server.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.rtsbuilding.rtsbuilding.util.RtsPlayerUtil;

public class RtsStorageSessionStore {

    private static final Map<UUID, Object> sessions = new HashMap<>();

    private RtsStorageSessionStore() {}

    public static Object getSession(EntityPlayer player) {
        return player != null ? sessions.get(RtsPlayerUtil.getUUID(player)) : null;
    }

    public static void putSession(EntityPlayer player, Object session) {
        if (player != null) {
            sessions.put(RtsPlayerUtil.getUUID(player), session);
        }
    }

    public static void removeSession(EntityPlayer player) {
        if (player != null) {
            sessions.remove(RtsPlayerUtil.getUUID(player));
        }
    }

    public static void clearAll() {
        sessions.clear();
    }
}
