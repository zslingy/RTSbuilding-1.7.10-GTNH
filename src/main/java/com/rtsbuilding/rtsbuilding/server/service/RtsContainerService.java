package com.rtsbuilding.rtsbuilding.server.service;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public class RtsContainerService {

    public static boolean isAe2Linked(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        return session.isAe2Linked();
    }

    public static boolean isContainerLinked(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        return session.isContainerLinked();
    }

    public static void clearAe2Link(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        session.clearAe2Link();
    }

    public static List<LinkedStorageRef> getLinkedStorages(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        return session.getLinkedStorages();
    }

    public static void tryPopulateFromAe2(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        RtsStorageManager.tryPopulateFromAe2(player, session);
    }

    public static void refreshAe2Data(EntityPlayerMP player) {
        RtsStorageSession session = RtsSessionService.getSession(player);
        RtsStorageManager.refreshAe2Data(player, session);
    }
}
