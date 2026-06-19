package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;

public final class BlueprintWriters {

    private BlueprintWriters() {}

    public static void writeBlueprint(OutputStream out, RtsBlueprint blueprint) {
        if (out == null || blueprint == null) return;
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound metadata = new NBTTagCompound();
        metadata.setString("name", blueprint.getName());
        metadata.setString("sourceName", blueprint.getSourceName());
        metadata.setString(
            "format",
            blueprint.getFormat()
                .name());
        metadata.setInteger(
            "sizeX",
            blueprint.getSize()
                .getX());
        metadata.setInteger(
            "sizeY",
            blueprint.getSize()
                .getY());
        metadata.setInteger(
            "sizeZ",
            blueprint.getSize()
                .getZ());
        root.setTag("metadata", metadata);
        NBTTagList blockList = new NBTTagList();
        for (RtsBlueprintBlock block : blueprint.getBlocks()) {
            NBTTagCompound blockTag = new NBTTagCompound();
            blockTag.setInteger(
                "x",
                block.getRelativePos()
                    .getX());
            blockTag.setInteger(
                "y",
                block.getRelativePos()
                    .getY());
            blockTag.setInteger(
                "z",
                block.getRelativePos()
                    .getZ());
            blockTag.setString("stateId", block.getStateId());
            blockTag.setInteger("meta", block.getMeta());
            if (block.hasBlockEntityTag()) {
                blockTag.setTag("blockEntityTag", block.getBlockEntityTag());
            }
            if (block.isMissingBlock()) {
                blockTag.setString("missingBlockId", block.getMissingBlockId());
            }
            blockList.appendTag(blockTag);
        }
        root.setTag("blocks", blockList);
        try {
            CompressedStreamTools.writeCompressed(root, new DataOutputStream(out));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write blueprint: " + blueprint.getName(), e);
        }
    }
}
