package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class HistoryExecutor {

    private final ServerHistoryManager manager;

    public HistoryExecutor(ServerHistoryManager manager) {
        this.manager = manager;
    }

    public boolean undo(EntityPlayerMP player) {
        HistoryEntry entry = manager.popHistory(player);
        if (entry == null) return false;

        World world = player.worldObj;
        for (int i = entry.records.size() - 1; i >= 0; i--) {
            HistoryBlockRecord r = entry.records.get(i);
            if (r.oldBlock != null) {
                world.setBlock(r.x, r.y, r.z, r.oldBlock, r.oldMeta, 3);
                if (r.oldTile != null) {
                    TileEntity te = world.getTileEntity(r.x, r.y, r.z);
                    if (te != null) {
                        te.readFromNBT(r.oldTile);
                        te.xCoord = r.x;
                        te.yCoord = r.y;
                        te.zCoord = r.z;
                    }
                }
            } else {
                world.setBlockToAir(r.x, r.y, r.z);
            }
        }

        manager.pushRedo(player, entry);
        return true;
    }

    public boolean redo(EntityPlayerMP player) {
        HistoryEntry entry = manager.popRedo(player);
        if (entry == null) return false;

        World world = player.worldObj;
        for (HistoryBlockRecord r : entry.records) {
            if (r.newBlock != null) {
                world.setBlock(r.x, r.y, r.z, r.newBlock, r.newMeta, 3);
                if (r.newTile != null) {
                    TileEntity te = world.getTileEntity(r.x, r.y, r.z);
                    if (te != null) {
                        te.readFromNBT(r.newTile);
                        te.xCoord = r.x;
                        te.yCoord = r.y;
                        te.zCoord = r.z;
                    }
                }
            } else {
                world.setBlockToAir(r.x, r.y, r.z);
            }
        }

        manager.pushHistory(player, entry);
        return true;
    }
}
