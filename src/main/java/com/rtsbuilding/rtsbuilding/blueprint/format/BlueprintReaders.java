package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.InputStream;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;

public final class BlueprintReaders {

    private BlueprintReaders() {}

    /** 根据格式分发到对应的 Reader */
    public static RtsBlueprint readBlueprint(InputStream in, BlueprintFormat format, String name, String sourceName) {
        if (name == null || name.isEmpty()) {
            name = "imported";
        }
        switch (format) {
            case VANILLA_NBT:
                return VanillaStructureNbtReader.read(in, name);
            case SPONGE_SCHEM:
                return SpongeSchemReader.read(in, name);
            case LITEMATIC:
                return LitematicReader.read(in, name);
            case BUILDING_GADGETS_JSON:
                return BuildingGadgetsTemplateReader.read(in, name);
            default:
                return null;
        }
    }
}
