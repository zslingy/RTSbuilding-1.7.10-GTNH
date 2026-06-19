package com.rtsbuilding.rtsbuilding.server.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

public final class RtsPlacedRecoveryService {

    private static final RtsPlacedRecoveryService INSTANCE = new RtsPlacedRecoveryService();

    private RtsPlacedRecoveryService() {}

    public static RtsPlacedRecoveryService getInstance() {
        return INSTANCE;
    }

    public List<RecoveryBlock> scanRecoverable(EntityPlayerMP player) {
        List<RecoveryBlock> result = new ArrayList<RecoveryBlock>();
        List<RtsPendingPlacementService.PendingBlock> pendings = RtsPendingPlacementService.getInstance()
            .getPendings(player);
        for (RtsPendingPlacementService.PendingBlock pb : pendings) {
            result.add(new RecoveryBlock(pb.x, pb.y, pb.z, pb.itemId, pb.meta, pb.face));
        }
        return result;
    }

    public static final class RecoveryBlock {

        public final int x, y, z;
        public final String itemId;
        public final int meta;
        public final byte face;

        RecoveryBlock(int x, int y, int z, String itemId, int meta, byte face) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemId = itemId;
            this.meta = meta;
            this.face = face;
        }
    }
}
