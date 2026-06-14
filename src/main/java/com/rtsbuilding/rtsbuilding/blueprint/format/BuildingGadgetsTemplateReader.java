package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.InputStream;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

/**
 * Building Gadgets 模板格式读取器。
 * 阶段6基础：返回空蓝图占位。完整的 JSON→NBT→BlockState 解析链
 * 需要适配 1.7.10 的 Block.getBlockFromName() / GameData API，
 * 属于后续阶段增强项。
 */
public final class BuildingGadgetsTemplateReader {

    private BuildingGadgetsTemplateReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        RtsbuildingMod.LOGGER.warn(
            "BuildingGadgetsTemplateReader: full BG JSON parsing not yet implemented for 1.7.10, returning empty blueprint");
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        return RtsBlueprint.create(
            name != null ? name : "bg_import",
            "bg_template",
            BlueprintFormat.BUILDING_GADGETS_JSON,
            new RtsBlockPos(0, 0, 0),
            new java.util.ArrayList<>());
    }
}
