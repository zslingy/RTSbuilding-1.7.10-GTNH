package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.InputStream;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

/**
 * Litematica 格式读取器。
 * 阶段6基础：返回空蓝图占位。完整的压缩NBT→Palette→BlockState 解析链
 * 需要适配 1.7.10 的 CompressedStreamTools / NBTTagCompound API，
 * 属于后续阶段增强项。
 */
public final class LitematicReader {

    private LitematicReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        RtsbuildingMod.LOGGER
            .warn("LitematicReader: full Litematica parsing not yet implemented for 1.7.10, returning empty blueprint");
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        return RtsBlueprint.create(
            name != null ? name : "litematic_import",
            "litematic_file",
            BlueprintFormat.LITEMATIC,
            new RtsBlockPos(0, 0, 0),
            new java.util.ArrayList<>());
    }
}
