package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraft.nbt.NBTTagCompound;

import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public class RtsBlueprintBlock {

    private final RtsBlockPos relativePos;
    private final String stateId;
    private final int meta;
    private final NBTTagCompound blockEntityTag;
    private final String missingBlockId;

    public RtsBlueprintBlock(RtsBlockPos relativePos, String stateId, int meta, NBTTagCompound blockEntityTag,
        String missingBlockId) {
        this.relativePos = relativePos;
        this.stateId = stateId;
        this.meta = meta;
        this.blockEntityTag = blockEntityTag != null ? blockEntityTag : new NBTTagCompound();
        this.missingBlockId = missingBlockId != null ? missingBlockId : "";
    }

    public static RtsBlueprintBlock missing(RtsBlockPos relativePos, String missingBlockId,
        NBTTagCompound blockEntityTag) {
        return new RtsBlueprintBlock(
            relativePos,
            "minecraft:air",
            0,
            blockEntityTag != null ? blockEntityTag : new NBTTagCompound(),
            missingBlockId != null ? missingBlockId : "");
    }

    public RtsBlockPos getRelativePos() {
        return relativePos;
    }

    public String getStateId() {
        return stateId;
    }

    public int getMeta() {
        return meta;
    }

    public NBTTagCompound getBlockEntityTag() {
        return blockEntityTag;
    }

    public String getMissingBlockId() {
        return missingBlockId;
    }

    public boolean hasBlockEntityTag() {
        return blockEntityTag != null && !blockEntityTag.hasNoTags();
    }

    public boolean isMissingBlock() {
        return missingBlockId != null && !missingBlockId.trim()
            .isEmpty();
    }
}
