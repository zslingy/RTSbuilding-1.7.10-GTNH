package com.rtsbuilding.rtsbuilding.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

public class RtsBlueprint {

    private final String name;
    private final String sourceName;
    private final BlueprintFormat format;
    private final RtsBlockPos size;
    private final List<RtsBlueprintBlock> blocks;
    private final Map<String, Integer> requiredItems;

    public RtsBlueprint(String name, String sourceName, BlueprintFormat format, RtsBlockPos size,
        List<RtsBlueprintBlock> blocks, Map<String, Integer> requiredItems) {
        this.name = name;
        this.sourceName = sourceName;
        this.format = format;
        this.size = size;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<>(requiredItems));
    }

    public static RtsBlueprint create(String name, String sourceName, BlueprintFormat format, RtsBlockPos size,
        List<RtsBlueprintBlock> blocks) {
        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (RtsBlueprintBlock block : blocks) {
            if (block.isMissingBlock()) {
                continue;
            }
            String id = block.getStateId();
            if (id == null || id.isEmpty() || "minecraft:air".equals(id)) {
                continue;
            }
            Integer cur = requirements.get(id);
            requirements.put(id, cur == null ? 1 : cur + 1);
        }
        return new RtsBlueprint(name, sourceName, format, size, blocks, requirements);
    }

    public String getName() {
        return name;
    }

    public String getSourceName() {
        return sourceName;
    }

    public BlueprintFormat getFormat() {
        return format;
    }

    public RtsBlockPos getSize() {
        return size;
    }

    public List<RtsBlueprintBlock> getBlocks() {
        return blocks;
    }

    public Map<String, Integer> getRequiredItems() {
        return requiredItems;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public int getNonAirBlockCount() {
        int count = 0;
        for (RtsBlueprintBlock block : blocks) {
            if (!block.isMissingBlock() && !"minecraft:air".equals(block.getStateId())) {
                count++;
            }
        }
        return count;
    }
}
