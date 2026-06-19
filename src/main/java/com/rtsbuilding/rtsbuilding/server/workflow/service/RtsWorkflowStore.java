package com.rtsbuilding.rtsbuilding.server.workflow.service;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowSlotManager;

public final class RtsWorkflowStore {

    private static final String NBT_PLAYERS = "players";
    private static final String NBT_PLAYER_ID = "player_id";
    private static final String NBT_DIMS = "dimensions";
    private static final String NBT_DIM_ID = "dimension_id";
    private static final String NBT_SLOTS = "slots";

    private RtsWorkflowStore() {}

    public static void saveAll(MinecraftServer server, Map<UUID, Map<Integer, RtsWorkflowSlotManager>> playerSlots) {
        if (server == null || playerSlots == null) return;
        try {
            File file = getStoreFile(server);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            NBTTagCompound root = new NBTTagCompound();
            NBTTagList players = new NBTTagList();
            for (Map.Entry<UUID, Map<Integer, RtsWorkflowSlotManager>> playerEntry : playerSlots.entrySet()) {
                NBTTagCompound playerTag = new NBTTagCompound();
                playerTag.setString(
                    NBT_PLAYER_ID,
                    playerEntry.getKey()
                        .toString());
                NBTTagList dims = new NBTTagList();
                for (Map.Entry<Integer, RtsWorkflowSlotManager> dimEntry : playerEntry.getValue()
                    .entrySet()) {
                    NBTTagCompound dimTag = new NBTTagCompound();
                    dimTag.setInteger(NBT_DIM_ID, dimEntry.getKey());
                    dimTag.setTag(
                        NBT_SLOTS,
                        dimEntry.getValue()
                            .saveToNbt());
                    dims.appendTag(dimTag);
                }
                playerTag.setTag(NBT_DIMS, dims);
                players.appendTag(playerTag);
            }
            root.setTag(NBT_PLAYERS, players);
            CompressedStreamTools.writeCompressed(root, new java.io.FileOutputStream(file));
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("[Workflow] save failed", e);
        }
    }

    public static Map<Integer, RtsWorkflowSlotManager> loadPlayer(MinecraftServer server, UUID playerId) {
        Map<Integer, RtsWorkflowSlotManager> result = new ConcurrentHashMap<Integer, RtsWorkflowSlotManager>();
        if (server == null || playerId == null) return result;
        try {
            File file = getStoreFile(server);
            if (!file.exists()) return result;
            NBTTagCompound root = CompressedStreamTools.readCompressed(new java.io.FileInputStream(file));
            NBTTagList players = root.getTagList(NBT_PLAYERS, 10);
            for (int i = 0; i < players.tagCount(); i++) {
                NBTTagCompound playerTag = players.getCompoundTagAt(i);
                if (!playerId.toString()
                    .equals(playerTag.getString(NBT_PLAYER_ID))) continue;
                NBTTagList dims = playerTag.getTagList(NBT_DIMS, 10);
                for (int j = 0; j < dims.tagCount(); j++) {
                    NBTTagCompound dimTag = dims.getCompoundTagAt(j);
                    result.put(
                        dimTag.getInteger(NBT_DIM_ID),
                        RtsWorkflowSlotManager.loadFromNbt(dimTag.getCompoundTag(NBT_SLOTS)));
                }
                break;
            }
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("[Workflow] load failed", e);
        }
        return result;
    }

    private static File getStoreFile(MinecraftServer server) {
        return new File(server.getFile("."), "rtsbuilding/workflow_data.dat");
    }
}
