package com.rtsbuilding.rtsbuilding.blueprint.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.blueprint.format.BlueprintReaders;
import com.rtsbuilding.rtsbuilding.util.RtsBlockPos;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class BlueprintPlacementService {

    private static final int BLOCKS_PER_TICK = 64;
    private static final Map<UUID, PlacementJob> JOBS = new ConcurrentHashMap<>();

    private BlueprintPlacementService() {}

    public static BlueprintPlacementService create() {
        return new BlueprintPlacementService();
    }

    public static void queuePlacement(EntityPlayerMP player, String fileName, int anchorX, int anchorY, int anchorZ,
        byte rotateSteps) {
        if (player == null || fileName == null) {
            return;
        }
        if (!Config.areBlueprintsEnabled()) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[RTS] Blueprints are disabled in config."));
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        File bpDir = new File(server.getFile(""), "rtsbuilding-blueprints");
        File bpFile = new File(bpDir, fileName);
        if (!bpFile.isFile()) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[RTS] Blueprint file not found: " + fileName));
            return;
        }

        BlueprintFormat format = BlueprintFormat.fromFileName(fileName);
        RtsBlueprint blueprint;
        try (InputStream in = new FileInputStream(bpFile)) {
            blueprint = BlueprintReaders.readBlueprint(in, format, fileName, fileName);
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("BlueprintPlacementService: failed to read blueprint: {}", e.toString());
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[RTS] Failed to read blueprint: " + e.getMessage()));
            return;
        }

        if (blueprint == null || blueprint.getBlocks()
            .isEmpty()) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[RTS] Blueprint is empty or could not be parsed."));
            return;
        }

        int maxBlocks = Config.maxBlueprintBlocks();
        if (blueprint.getBlockCount() > maxBlocks) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "[RTS] Blueprint too large: "
                        + blueprint.getBlockCount()
                        + " blocks (max "
                        + maxBlocks
                        + ")"));
            return;
        }

        JOBS.put(
            player.getUniqueID(),
            new PlacementJob(
                blueprint,
                anchorX,
                anchorY,
                anchorZ,
                BlueprintTransform.normalizeSteps(rotateSteps),
                0,
                0,
                0,
                0));

        player.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.GREEN + "[RTS] Blueprint placement queued: "
                    + fileName
                    + " ("
                    + blueprint.getBlockCount()
                    + " blocks)"));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (JOBS.isEmpty()) {
            return;
        }

        UUID key = JOBS.keySet()
            .iterator()
            .next();
        PlacementJob job = JOBS.get(key);
        if (job == null) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        EntityPlayerMP player = null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) obj;
            if (p.getUniqueID()
                .equals(key)) {
                player = p;
                break;
            }
        }

        if (player == null) {
            JOBS.remove(key);
            return;
        }

        if (!Config.areBlueprintsEnabled()) {
            JOBS.remove(key);
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[RTS] Blueprints disabled during placement."));
            return;
        }

        World world = player.worldObj;
        int processed = 0;
        int placed = job.placedCount;
        int skippedMissing = job.skippedMissing;
        int skippedBlocked = job.skippedBlocked;
        int index = job.nextIndex;

        RtsBlockPos size = job.blueprint.getSize();
        RtsBlockPos centerOffset = BlueprintTransform
            .centerRotationOffset(size.getX(), size.getY(), size.getZ(), job.rotateSteps, 0, 0);

        while (index < job.blueprint.getBlocks()
            .size() && processed < BLOCKS_PER_TICK) {
            RtsBlueprintBlock block = job.blueprint.getBlocks()
                .get(index);
            index++;
            processed++;

            if (block.isMissingBlock()) {
                skippedMissing++;
                continue;
            }

            RtsBlockPos relativePos = block.getRelativePos();
            RtsBlockPos rotated = BlueprintTransform.rotate(relativePos, job.rotateSteps, 0, 0);
            RtsBlockPos offset = BlueprintTransform.applyOffset(rotated, centerOffset);

            int tx = job.anchorX + offset.getX();
            int ty = job.anchorY + offset.getY();
            int tz = job.anchorZ + offset.getZ();

            if (!BlueprintReplaceRules.canBlueprintReplace(world, tx, ty, tz)) {
                skippedBlocked++;
                continue;
            }

            String stateId = block.getStateId();
            int meta = block.getMeta();

            Block blk = null;
            if (stateId != null && !stateId.isEmpty() && !"minecraft:air".equals(stateId)) {
                String blockName = stateId;
                if (blockName.startsWith("minecraft:")) {
                    blockName = blockName.substring("minecraft:".length());
                }
                blk = Block.getBlockFromName(blockName);
            }

            if (blk == null) {
                skippedMissing++;
                continue;
            }

            boolean placedBlock = world.setBlock(tx, ty, tz, blk, meta, 3);
            if (!placedBlock) {
                skippedBlocked++;
                continue;
            }

            if (block.hasBlockEntityTag() && block.getBlockEntityTag() != null) {
                TileEntity te = world.getTileEntity(tx, ty, tz);
                if (te != null) {
                    try {
                        net.minecraft.nbt.NBTTagCompound tag = (net.minecraft.nbt.NBTTagCompound) block
                            .getBlockEntityTag()
                            .copy();
                        tag.setInteger("x", tx);
                        tag.setInteger("y", ty);
                        tag.setInteger("z", tz);
                        te.readFromNBT(tag);
                    } catch (Exception ignored) {}
                }
            }

            placed++;
        }

        if (index >= job.blueprint.getBlocks()
            .size()) {
            JOBS.remove(key);
            String msg = EnumChatFormatting.GREEN + "[RTS] Blueprint placed: "
                + placed
                + " blocks (skipped: missing="
                + skippedMissing
                + ", blocked="
                + skippedBlocked
                + ")";
            player.addChatMessage(new ChatComponentText(msg));
        } else {
            JOBS.put(
                key,
                new PlacementJob(
                    job.blueprint,
                    job.anchorX,
                    job.anchorY,
                    job.anchorZ,
                    job.rotateSteps,
                    index,
                    placed,
                    skippedMissing,
                    skippedBlocked));
        }
    }

    public static void clearPlayer(UUID uuid) {
        JOBS.remove(uuid);
    }

    public static PlacementJob getActiveJob(UUID uuid) {
        return JOBS.get(uuid);
    }

    static class PlacementJob {

        final RtsBlueprint blueprint;
        final int anchorX, anchorY, anchorZ;
        final int rotateSteps;
        final int nextIndex;
        final int placedCount;
        final int skippedMissing;
        final int skippedBlocked;

        PlacementJob(RtsBlueprint blueprint, int anchorX, int anchorY, int anchorZ, int rotateSteps, int nextIndex,
            int placedCount, int skippedMissing, int skippedBlocked) {
            this.blueprint = blueprint;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.rotateSteps = rotateSteps;
            this.nextIndex = nextIndex;
            this.placedCount = placedCount;
            this.skippedMissing = skippedMissing;
            this.skippedBlocked = skippedBlocked;
        }
    }
}
