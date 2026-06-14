package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

/**
 * 读取原版 NBT 结构文件（palette + blocks 格式）。
 * 1.7.10 适配：使用 NBTTagCompound / CompressedStreamTools / RtsBlockPos。
 */
public final class VanillaStructureNbtReader {

    private VanillaStructureNbtReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        try {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            return parse(root, name);
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("VanillaStructureNbtReader: failed to read '{}': {}", name, e.toString());
            return null;
        }
    }

    private static RtsBlueprint parse(NBTTagCompound root, String name) {
        // 验证结构文件格式
        if (!root.hasKey("palette") || !root.hasKey("blocks")) {
            RtsbuildingMod.LOGGER
                .warn("VanillaStructureNbtReader: '{}' is not a vanilla structure (missing palette/blocks)", name);
            return null;
        }

        // 读取 palette：方块状态 → 索引 映射
        NBTTagList paletteTag = root.getTagList("palette", 10); // NBTTagCompound type id
        List<PaletteEntry> palette = new ArrayList<>(paletteTag.tagCount());
        for (int i = 0; i < paletteTag.tagCount(); i++) {
            NBTTagCompound entry = paletteTag.getCompoundTagAt(i);
            palette.add(readPaletteEntry(entry));
        }

        // 读取 size
        RtsBlockPos size = readSize(root);

        // 读取 blocks 列表
        NBTTagList blockList = root.getTagList("blocks", 10);
        List<RtsBlueprintBlock> blocks = new ArrayList<>();

        for (int i = 0; i < blockList.tagCount(); i++) {
            NBTTagCompound blockTag = blockList.getCompoundTagAt(i);
            int stateIndex = blockTag.getInteger("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) continue;

            PaletteEntry pe = palette.get(stateIndex);
            if (pe == null) continue;

            RtsBlockPos pos = readPos(blockTag);
            NBTTagCompound beTag = blockTag.hasKey("nbt") ? blockTag.getCompoundTag("nbt") : new NBTTagCompound();

            if (pe.isMissing()) {
                blocks.add(RtsBlueprintBlock.missing(pos, pe.blockId, beTag));
                continue;
            }

            // 跳过空气
            String regName = Block.blockRegistry.getNameForObject(pe.block);
            if (regName == null || "minecraft:air".equals(regName)) continue;

            blocks.add(new RtsBlueprintBlock(pos, regName, pe.meta, beTag, ""));
        }

        return RtsBlueprint.create(cleanName(name), name, BlueprintFormat.VANILLA_NBT, size, blocks);
    }

    /** 从 palette 条目中解析方块ID和metadata */
    private static PaletteEntry readPaletteEntry(NBTTagCompound entry) {
        String blockName = entry.getString("Name");
        if (blockName == null || blockName.isEmpty()) {
            return PaletteEntry.missing("unknown");
        }

        Block block = Block.getBlockFromName(blockName);
        if (block == null) {
            return PaletteEntry.missing(blockName);
        }

        int meta = 0;
        // 尝试从 Properties 推断 metadata（简化：默认 0）
        if (entry.hasKey("Properties", 10)) {
            NBTTagCompound props = entry.getCompoundTag("Properties");
            meta = inferMeta(props);
        }

        return PaletteEntry.found(block, blockName, meta);
    }

    /** 从 Properties NBT 推断 metadata（1.7.10 简化版，默认返回 0） */
    private static int inferMeta(NBTTagCompound props) {
        // 1.7.10 中 Properties → metadata 的映射因方块而异。
        // 作为基础实现，返回默认值 0。高级映射留给后续阶段。
        // 已知简单映射：facing=2→meta=2, axis=y→meta=0 等
        if (props.hasKey("facing")) {
            String facing = props.getString("facing");
            if ("north".equals(facing)) return 2;
            if ("south".equals(facing)) return 3;
            if ("west".equals(facing)) return 4;
            if ("east".equals(facing)) return 5;
        }
        return 0;
    }

    private static RtsBlockPos readSize(NBTTagCompound root) {
        if (!root.hasKey("size")) return new RtsBlockPos(0, 0, 0);
        // 优先尝试 int-array 格式
        if (root.hasKey("size", 11)) {
            int[] arr = root.getIntArray("size");
            if (arr.length >= 3) return new RtsBlockPos(arr[0], arr[1], arr[2]);
        }
        // 回退: List[Int] 格式 — 用 copy+removeTag 绕过 MCP 缺失的 getIntAt
        return readIntList3(root, "size");
    }

    private static RtsBlockPos readPos(NBTTagCompound blockTag) {
        if (!blockTag.hasKey("pos")) return new RtsBlockPos(0, 0, 0);
        if (blockTag.hasKey("pos", 11)) {
            int[] arr = blockTag.getIntArray("pos");
            if (arr.length >= 3) return new RtsBlockPos(arr[0], arr[1], arr[2]);
        }
        return readIntList3(blockTag, "pos");
    }

    /** 从 NBTTagList[Int] 读取3个整数 (MCP stable_12 缺少 getIntAt) */
    private static RtsBlockPos readIntList3(NBTTagCompound tag, String key) {
        NBTTagList list = tag.getTagList(key, 3);
        if (list.tagCount() < 3) return new RtsBlockPos(0, 0, 0);
        // copy 后 removeTag 逐个取出
        NBTTagList copy = (NBTTagList) list.copy();
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            net.minecraft.nbt.NBTBase base = copy.removeTag(0);
            values[i] = (base instanceof net.minecraft.nbt.NBTTagInt)
                ? ((net.minecraft.nbt.NBTTagInt) base).func_150287_d()
                : 0;
        }
        return new RtsBlockPos(values[0], values[1], values[2]);
    }

    private static String cleanName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "Blueprint";
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    /** 内部 palette 条目 */
    private static final class PaletteEntry {

        final Block block; // null if missing
        final String blockId; // "minecraft:stone" 或 missing 时的原始字符串
        final int meta;

        private PaletteEntry(Block block, String blockId, int meta) {
            this.block = block;
            this.blockId = blockId;
            this.meta = meta;
        }

        static PaletteEntry found(Block block, String blockId, int meta) {
            return new PaletteEntry(block, blockId, meta);
        }

        static PaletteEntry missing(String blockId) {
            return new PaletteEntry(null, blockId, 0);
        }

        boolean isMissing() {
            return block == null;
        }
    }
}
