package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public final class SpongeSchemReader {

    private SpongeSchemReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        if (in == null) return empty(name);

        try (DataInputStream dis = new DataInputStream(in)) {
            NBTTagCompound root = CompressedStreamTools.read(dis);
            if (root == null) {
                return empty(name);
            }

            short width = root.getShort("Width");
            short height = root.getShort("Height");
            short length = root.getShort("Length");

            NBTTagCompound paletteTag = root.getCompoundTag("Palette");
            Map<Integer, String> palette = new HashMap<>();
            for (Object key : paletteTag.func_150296_c()) {
                String k = (String) key;
                int idx = paletteTag.getInteger(k);
                palette.put(idx, k);
            }

            byte[] blockData = root.getByteArray("BlockData");
            if (blockData == null || blockData.length == 0) {
                RtsbuildingMod.LOGGER.warn("SpongeSchemReader: BlockData is empty for {}", name);
                return empty(name);
            }

            List<RtsBlueprintBlock> blocks = new ArrayList<>();
            for (int i = 0; i < blockData.length; i++) {
                int idx = blockData[i] & 0xFF;
                int y = i / (width * length);
                int remaining = i % (width * length);
                int z = remaining / width;
                int x = remaining % width;

                String stateId = palette.getOrDefault(idx, "minecraft:air");
                if ("minecraft:air".equals(stateId)) continue;
                blocks.add(new RtsBlueprintBlock(new RtsBlockPos(x, y, z), stateId, 0, null, null));
            }

            RtsBlockPos size = new RtsBlockPos(width, height, length);
            RtsbuildingMod.LOGGER.info("SpongeSchemReader: parsed {} blocks from {}", blocks.size(), name);
            return RtsBlueprint.create(
                name != null ? name : "schem_import",
                "schematic_file",
                BlueprintFormat.SPONGE_SCHEM,
                size,
                blocks);

        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("SpongeSchemReader: failed to parse {}", name, e);
            return empty(name);
        }
    }

    private static RtsBlueprint empty(String name) {
        return RtsBlueprint.create(
            name != null ? name : "schem_import",
            "schematic_file",
            BlueprintFormat.SPONGE_SCHEM,
            new RtsBlockPos(0, 0, 0),
            new ArrayList<>());
    }
}
