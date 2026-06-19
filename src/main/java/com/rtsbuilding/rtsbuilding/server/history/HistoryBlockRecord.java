package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public final class HistoryBlockRecord {

    public final int x, y, z;
    public final Block oldBlock;
    public final int oldMeta;
    public final NBTTagCompound oldTile;
    public final Block newBlock;
    public final int newMeta;
    public final NBTTagCompound newTile;

    public HistoryBlockRecord(int x, int y, int z, Block oldBlock, int oldMeta, NBTTagCompound oldTile, Block newBlock,
        int newMeta, NBTTagCompound newTile) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.oldBlock = oldBlock;
        this.oldMeta = oldMeta;
        this.oldTile = oldTile;
        this.newBlock = newBlock;
        this.newMeta = newMeta;
        this.newTile = newTile;
    }

    public static HistoryBlockRecord fromWorld(int x, int y, int z, net.minecraft.world.World world, Block oldBlock,
        int oldMeta) {
        NBTTagCompound oldTile = null;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null) {
            oldTile = new NBTTagCompound();
            te.writeToNBT(oldTile);
        }
        return new HistoryBlockRecord(
            x,
            y,
            z,
            oldBlock,
            oldMeta,
            oldTile,
            world.getBlock(x, y, z),
            world.getBlockMetadata(x, y, z),
            null);
    }
}
