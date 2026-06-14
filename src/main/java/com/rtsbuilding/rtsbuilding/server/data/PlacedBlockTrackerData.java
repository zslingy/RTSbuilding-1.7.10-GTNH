package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.NBTTagCompound;

import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public class PlacedBlockTrackerData {

    private RtsBlockPos pos;
    private String blockId;
    private int meta;

    public PlacedBlockTrackerData() {}

    public PlacedBlockTrackerData(RtsBlockPos pos, String blockId, int meta) {
        this.pos = pos;
        this.blockId = blockId;
        this.meta = meta;
    }

    public RtsBlockPos getPos() {
        return pos;
    }

    public String getBlockId() {
        return blockId;
    }

    public int getMeta() {
        return meta;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("x", pos != null ? pos.getX() : 0);
        tag.setInteger("y", pos != null ? pos.getY() : 0);
        tag.setInteger("z", pos != null ? pos.getZ() : 0);
        tag.setString("blockId", blockId != null ? blockId : "");
        tag.setInteger("meta", meta);
    }

    public static PlacedBlockTrackerData readFromNBT(NBTTagCompound tag) {
        return new PlacedBlockTrackerData(
            new RtsBlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")),
            tag.getString("blockId"),
            tag.getInteger("meta"));
    }
}
