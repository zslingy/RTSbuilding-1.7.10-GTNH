package com.rtsbuilding.rtsbuilding.blueprint.format;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public final class BuildingGadgetsTemplateReader {

    private BuildingGadgetsTemplateReader() {}

    public static RtsBlueprint read(InputStream in, String name) {
        if (in == null) return empty(name);

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            String json = sb.toString();
            List<RtsBlueprintBlock> blocks = new ArrayList<>();
            int maxX = 1;
            int maxY = 1;
            int maxZ = 1;

            int posStart = 0;
            while ((posStart = json.indexOf("\"x\":", posStart)) != -1) {
                int xEnd = json.indexOf(',', posStart);
                if (xEnd == -1) break;
                int x = parseInt(json, posStart + 4, xEnd);

                int yStart = json.indexOf("\"y\":", xEnd);
                if (yStart == -1) break;
                int yEnd = json.indexOf(',', yStart);
                if (yEnd == -1) yEnd = json.indexOf('}', yStart);
                int y = parseInt(json, yStart + 4, yEnd);

                int zStart = json.indexOf("\"z\":", yEnd);
                if (zStart == -1) break;
                int zEnd = json.indexOf(',', zStart);
                if (zEnd == -1) zEnd = json.indexOf('}', zStart);
                int z = parseInt(json, zStart + 4, zEnd);

                String stateId = "minecraft:stone";
                int stateStart = json.lastIndexOf("\"state\"", posStart);
                if (stateStart > posStart - 200 && stateStart < posStart) {
                    int valStart = json.indexOf('"', stateStart + 8);
                    if (valStart != -1) {
                        int valEnd = json.indexOf('"', valStart + 1);
                        if (valEnd != -1) {
                            stateId = json.substring(valStart + 1, valEnd);
                        }
                    }
                }

                blocks.add(new RtsBlueprintBlock(new RtsBlockPos(x, y, z), stateId, 0, null, null));
                if (x + 1 > maxX) maxX = x + 1;
                if (y + 1 > maxY) maxY = y + 1;
                if (z + 1 > maxZ) maxZ = z + 1;

                posStart = zEnd + 1;
            }

            RtsBlockPos size = new RtsBlockPos(maxX, maxY, maxZ);
            RtsbuildingMod.LOGGER.info("BuildingGadgetsTemplateReader: parsed {} blocks from {}", blocks.size(), name);
            return RtsBlueprint.create(
                name != null ? name : "bg_import",
                "bg_template",
                BlueprintFormat.BUILDING_GADGETS_JSON,
                size,
                blocks);

        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("BuildingGadgetsTemplateReader: failed to parse {}", name, e);
            return empty(name);
        }
    }

    private static int parseInt(String s, int start, int end) {
        try {
            return Integer.parseInt(
                s.substring(start, end)
                    .trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static RtsBlueprint empty(String name) {
        return RtsBlueprint.create(
            name != null ? name : "bg_import",
            "bg_template",
            BlueprintFormat.BUILDING_GADGETS_JSON,
            new RtsBlockPos(0, 0, 0),
            new ArrayList<>());
    }
}
