package com.rtsbuilding.rtsbuilding.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

public final class RtsPendingPlacementService {

    private static final RtsPendingPlacementService INSTANCE = new RtsPendingPlacementService();

    private final Map<UUID, List<PendingBlock>> playerPendings = new HashMap<UUID, List<PendingBlock>>();

    private RtsPendingPlacementService() {}

    public static RtsPendingPlacementService getInstance() {
        return INSTANCE;
    }

    public void addPending(EntityPlayerMP player, String itemId, int meta, int x, int y, int z, byte face) {
        if (player == null || itemId == null) return;
        UUID id = player.getUniqueID();
        List<PendingBlock> list = playerPendings.get(id);
        if (list == null) {
            list = new ArrayList<PendingBlock>();
            playerPendings.put(id, list);
        }
        list.add(new PendingBlock(x, y, z, itemId, meta, face, System.currentTimeMillis()));
    }

    public List<PendingBlock> getPendings(EntityPlayerMP player) {
        if (player == null) return new ArrayList<PendingBlock>();
        List<PendingBlock> list = playerPendings.get(player.getUniqueID());
        return list != null ? new ArrayList<PendingBlock>(list) : new ArrayList<PendingBlock>();
    }

    public boolean removePending(EntityPlayerMP player, int index) {
        if (player == null) return false;
        List<PendingBlock> list = playerPendings.get(player.getUniqueID());
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            return true;
        }
        return false;
    }

    public void clearPendings(EntityPlayerMP player) {
        if (player != null) playerPendings.remove(player.getUniqueID());
    }

    public boolean hasPendings(EntityPlayerMP player) {
        if (player == null) return false;
        List<PendingBlock> list = playerPendings.get(player.getUniqueID());
        return list != null && !list.isEmpty();
    }

    public static final class PendingBlock {

        public final int x, y, z;
        public final String itemId;
        public final int meta;
        public final byte face;
        public final long timestamp;

        PendingBlock(int x, int y, int z, String itemId, int meta, byte face, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemId = itemId;
            this.meta = meta;
            this.face = face;
            this.timestamp = timestamp;
        }
    }
}
