package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public final class LitematicReader {

    private LitematicReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        if (in == null) return empty(name);

        try (DataInputStream dis = new DataInputStream(in)) {
            NBTTagCompound root = CompressedStreamTools.read(dis);
            if (root == null) return empty(name);

            NBTTagCompound meta = root.getCompoundTag("Metadata");
            if (meta.hasNoTags()) return empty(name);

            NBTTagCompound enclosing = meta.getCompoundTag("EnclosingSize");
            int totalX = enclosing.getInteger("x");
            int totalY = enclosing.getInteger("y");
            int totalZ = enclosing.getInteger("z");

            NBTTagCompound regions = root.getCompoundTag("Regions");
            List<RtsBlueprintBlock> allBlocks = new ArrayList<>();

            for (Object regionKey : regions.func_150296_c()) {
                String key = (String) regionKey;
                NBTTagCompound region = regions.getCompoundTag(key);
                if (region.hasNoTags()) continue;

                NBTTagCompound regionPos = region.getCompoundTag("Position");
                int regionOriginX = regionPos.getInteger("x");
                int regionOriginY = regionPos.getInteger("y");
                int regionOriginZ = regionPos.getInteger("z");

                NBTTagCompound regionSize = region.getCompoundTag("Size");
                int rx = regionSize.getInteger("x");
                int ry = regionSize.getInteger("y");
                int rz = regionSize.getInteger("z");

                NBTTagList paletteList = region.getTagList("BlockStatePalette", 10);
                if (paletteList.tagCount() == 0) continue;

                List<String> palette = new ArrayList<>();
                for (int i = 0; i < paletteList.tagCount(); i++) {
                    NBTTagCompound stateTag = paletteList.getCompoundTagAt(i);
                    String blockName = stateTag.getString("Name");
                    palette.add(blockName);
                }

                int bitsPerBlock = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
                long mask = (1L << bitsPerBlock) - 1;
                int blocksPerLong = 64 / bitsPerBlock;

                byte[] blockData = region.getByteArray("BlockStates");
                long[] stateData = null;

                if (blockData.length > 0) {
                    stateData = new long[blockData.length / 8];
                    java.nio.ByteBuffer.wrap(blockData)
                        .asLongBuffer()
                        .get(stateData);
                } else {
                    NBTTagList blockStates = region.getTagList("BlockStates", 7);
                    if (blockStates.tagCount() == 0) continue;
                    stateData = new long[blockStates.tagCount()];
                    for (int i = 0; i < blockStates.tagCount(); i++) {
                        try {
                            java.lang.reflect.Field f = NBTTagList.class.getDeclaredField("tagList");
                            f.setAccessible(true);
                            java.util.List<?> tl = (java.util.List<?>) f.get(blockStates);
                            Object tag = tl.get(i);
                            if (tag instanceof net.minecraft.nbt.NBTTagLong) {
                                stateData[i] = ((net.minecraft.nbt.NBTTagLong) tag).func_150291_c();
                            }
                        } catch (Exception e) {
                            RtsbuildingMod.LOGGER.warn("LitematicReader: cannot parse BlockStates, skipping region");
                            stateData = null;
                            break;
                        }
                    }
                    if (stateData == null) continue;
                }

                int index = 0;
                for (long val : stateData) {
                    for (int b = 0; b < blocksPerLong && index < rx * ry * rz; b++) {
                        int paletteIdx = (int) ((val >>> (b * bitsPerBlock)) & mask);
                        if (paletteIdx >= palette.size()) {
                            index++;
                            continue;
                        }
                        String stateId = palette.get(paletteIdx);
                        if ("minecraft:air".equals(stateId)) {
                            index++;
                            continue;
                        }
                        int by = index / (rx * rz);
                        int rem = index % (rx * rz);
                        int bz = rem / rx;
                        int bx = rem % rx;
                        int gx = regionOriginX + bx;
                        int gy = regionOriginY + by;
                        int gz = regionOriginZ + bz;
                        allBlocks.add(new RtsBlueprintBlock(new RtsBlockPos(gx, gy, gz), stateId, 0, null, null));
                        index++;
                    }
                }
            }

            RtsBlockPos size = new RtsBlockPos(totalX, totalY, totalZ);
            RtsbuildingMod.LOGGER.info("LitematicReader: parsed {} blocks from {}", allBlocks.size(), name);
            return RtsBlueprint.create(
                name != null ? name : "litematic_import",
                "litematic_file",
                BlueprintFormat.LITEMATIC,
                size,
                allBlocks);

        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("LitematicReader: failed to parse {}", name, e);
            return empty(name);
        }
    }

    private static RtsBlueprint empty(String name) {
        return RtsBlueprint.create(
            name != null ? name : "litematic_import",
            "litematic_file",
            BlueprintFormat.LITEMATIC,
            new RtsBlockPos(0, 0, 0),
            new ArrayList<>());
    }
}
