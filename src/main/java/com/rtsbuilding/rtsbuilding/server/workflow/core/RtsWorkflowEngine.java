package com.rtsbuilding.rtsbuilding.server.workflow.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowStore;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSyncService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowTimeoutService;

public final class RtsWorkflowEngine implements IWorkflowEngine {

    private static final RtsWorkflowEngine INSTANCE = new RtsWorkflowEngine();

    private final Map<UUID, Map<Integer, RtsWorkflowSlotManager>> playerSlots = new ConcurrentHashMap<UUID, Map<Integer, RtsWorkflowSlotManager>>();
    private final Map<UUID, EntityPlayerMP> playerRefs = new ConcurrentHashMap<UUID, EntityPlayerMP>();
    private final RtsWorkflowEventBus eventBus = new RtsWorkflowEventBus();
    private final RtsWorkflowSyncService syncService = new RtsWorkflowSyncService();
    private RtsWorkflowTimeoutService timeoutService;

    private RtsWorkflowEngine() {}

    public static RtsWorkflowEngine getInstance() {
        return INSTANCE;
    }

    public void startTimeoutService(Duration checkInterval, Duration maxIdleTime) {
        if (timeoutService == null) {
            timeoutService = new RtsWorkflowTimeoutService(this, playerSlots);
            timeoutService.start(checkInterval, maxIdleTime);
        }
    }

    public void stopTimeoutService() {
        if (timeoutService != null) {
            timeoutService.stop();
            timeoutService = null;
        }
    }

    RtsWorkflowEntry findEntry(UUID playerId, int dimensionId, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimensionId);
        return slots == null ? null : slots.findEntryById(entryId);
    }

    void removeEntry(UUID playerId, int dimensionId, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimensionId);
        if (slots == null || !slots.removeEntryById(entryId)) return;
        EntityPlayerMP player = findPlayerByUUID(playerId);
        if (player != null) syncService.notifyPlayer(player, slots);
    }

    void notifyPlayer(UUID playerId, int dimensionId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimensionId);
        EntityPlayerMP player = findPlayerByUUID(playerId);
        if (slots != null && player != null) syncService.notifyPlayer(player, slots);
    }

    void fireEvent(WorkflowEventType type, UUID playerId, int entryId, RtsWorkflowEntry entry) {
        eventBus.fire(
            new WorkflowEvent(type, playerId, entryId, entry == null ? RtsWorkflowStatus.idle() : entry.snapshot()));
    }

    @Override
    public Optional<RtsWorkflowToken> start(EntityPlayerMP player, RtsWorkflowType type, RtsWorkflowPriority priority,
        int totalBlocks) {
        if (player == null || player.worldObj == null || type == null) return Optional.empty();
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        RtsWorkflowEntry entry = slots.addEntry(priority);
        if (entry == null) {
            RtsbuildingMod.LOGGER.warn("[Workflow] {} workflow slots full", player.getDisplayName());
            return Optional.empty();
        }
        entry.setType(type);
        entry.setTotalBlocks(totalBlocks);
        playerRefs.put(player.getUniqueID(), player);
        int dimensionId = player.worldObj.provider.dimensionId;
        fireEvent(WorkflowEventType.STARTED, player.getUniqueID(), entry.id(), entry);
        syncService.notifyPlayer(player, slots);
        return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entry.id(), dimensionId, this));
    }

    @Override
    public Optional<RtsWorkflowToken> from(EntityPlayerMP player, int entryId) {
        if (player == null || player.worldObj == null) return Optional.empty();
        playerRefs.put(player.getUniqueID(), player);
        int dimensionId = player.worldObj.provider.dimensionId;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimensionId);
        if (slots == null || slots.findEntryById(entryId) == null) return Optional.empty();
        return Optional.of(new RtsWorkflowToken(player.getUniqueID(), entryId, dimensionId, this));
    }

    @Override
    public Optional<RtsWorkflowToken> lastActive(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) return Optional.empty();
        playerRefs.put(player.getUniqueID(), player);
        int dimensionId = player.worldObj.provider.dimensionId;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), dimensionId);
        if (slots == null || slots.lastActive() == null) return Optional.empty();
        return Optional.of(
            new RtsWorkflowToken(
                player.getUniqueID(),
                slots.lastActive()
                    .id(),
                dimensionId,
                this));
    }

    @Override
    public void addListener(RtsWorkflowEventListener listener) {
        eventBus.addListener(listener);
    }

    @Override
    public void removeListener(RtsWorkflowEventListener listener) {
        eventBus.removeListener(listener);
    }

    @Override
    public RtsWorkflowStatus getProgress(RtsWorkflowToken token) {
        return token == null ? RtsWorkflowStatus.idle() : token.getProgress();
    }

    @Override
    public RtsWorkflowStatus getProgress(EntityPlayerMP player, int entryId) {
        if (player == null || player.worldObj == null) return RtsWorkflowStatus.idle();
        RtsWorkflowEntry entry = findEntry(player.getUniqueID(), player.worldObj.provider.dimensionId, entryId);
        return entry == null ? RtsWorkflowStatus.idle() : entry.snapshot();
    }

    @Override
    public List<RtsWorkflowStatus> getAllProgress(EntityPlayerMP player) {
        List<RtsWorkflowStatus> result = new ArrayList<RtsWorkflowStatus>();
        if (player == null || player.worldObj == null) return result;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), player.worldObj.provider.dimensionId);
        if (slots == null) return result;
        for (RtsWorkflowEntry entry : slots.occupiedEntries()) result.add(entry.snapshot());
        return result;
    }

    public RtsWorkflowSlotManager getSlotManager(EntityPlayerMP player) {
        return player == null || player.worldObj == null ? null
            : getSlots(player.getUniqueID(), player.worldObj.provider.dimensionId);
    }

    @Override
    public boolean hasActiveWorkflow(EntityPlayerMP player) {
        return activeWorkflowCount(player) > 0;
    }

    @Override
    public int activeWorkflowCount(EntityPlayerMP player) {
        RtsWorkflowSlotManager slots = player == null || player.worldObj == null ? null
            : getSlots(player.getUniqueID(), player.worldObj.provider.dimensionId);
        return slots == null ? 0 : slots.activeCount();
    }

    @Override
    public int occupiedSlotCount(EntityPlayerMP player) {
        RtsWorkflowSlotManager slots = player == null || player.worldObj == null ? null
            : getSlots(player.getUniqueID(), player.worldObj.provider.dimensionId);
        return slots == null ? 0 : slots.occupiedCount();
    }

    @Override
    public boolean isFull(EntityPlayerMP player) {
        return occupiedSlotCount(player) >= RtsWorkflowSlotManager.MAX_SLOTS;
    }

    @Override
    public void deleteWorkflow(EntityPlayerMP player, int entryId) {
        if (player == null || player.worldObj == null) return;
        RtsWorkflowEntry entry = findEntry(player.getUniqueID(), player.worldObj.provider.dimensionId, entryId);
        if (entry != null) fireEvent(WorkflowEventType.CANCELLED, player.getUniqueID(), entryId, entry);
        removeEntry(player.getUniqueID(), player.worldObj.provider.dimensionId, entryId);
    }

    @Override
    public void cancelAll(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) return;
        RtsWorkflowSlotManager slots = getSlots(player.getUniqueID(), player.worldObj.provider.dimensionId);
        if (slots == null) return;
        for (RtsWorkflowEntry entry : slots.occupiedEntries())
            fireEvent(WorkflowEventType.CANCELLED, player.getUniqueID(), entry.id(), entry);
        slots.clear();
        syncService.sendIdle(player);
    }

    @Override
    public void clearPlayerData(UUID playerId) {
        playerSlots.remove(playerId);
        playerRefs.remove(playerId);
    }

    @Override
    public void clearAllData() {
        playerSlots.clear();
        playerRefs.clear();
    }

    @Override
    public boolean isEntryPaused(UUID playerId, int dimensionId, int entryId) {
        RtsWorkflowEntry entry = findEntry(playerId, dimensionId, entryId);
        return entry != null && entry.paused();
    }

    @Override
    public int cleanupStaleWorkflows(Duration maxIdleTime) {
        int total = 0;
        long maxIdleMillis = maxIdleTime.toMillis();
        for (Map.Entry<UUID, Map<Integer, RtsWorkflowSlotManager>> playerEntry : playerSlots.entrySet()) {
            for (Map.Entry<Integer, RtsWorkflowSlotManager> dimEntry : playerEntry.getValue()
                .entrySet()) {
                for (Integer entryId : dimEntry.getValue()
                    .removeStaleEntries(maxIdleMillis)) {
                    fireEvent(WorkflowEventType.TIMEOUT, playerEntry.getKey(), entryId, null);
                    total++;
                }
            }
        }
        return total;
    }

    public void firePipelineEvent(EntityPlayerMP player, int entryId, WorkflowEventType type) {
        if (player == null || player.worldObj == null) return;
        RtsWorkflowEntry entry = findEntry(player.getUniqueID(), player.worldObj.provider.dimensionId, entryId);
        if (entry != null) fireEvent(type, player.getUniqueID(), entryId, entry);
    }

    public void saveAll(MinecraftServer server) {
        RtsWorkflowStore.saveAll(server, playerSlots);
    }

    public void loadPlayerFromStore(MinecraftServer server, EntityPlayerMP player) {
        if (server == null || player == null) return;
        Map<Integer, RtsWorkflowSlotManager> loaded = RtsWorkflowStore.loadPlayer(server, player.getUniqueID());
        if (!loaded.isEmpty()) playerSlots.put(player.getUniqueID(), loaded);
    }

    private RtsWorkflowSlotManager getOrCreateSlots(EntityPlayerMP player) {
        playerRefs.put(player.getUniqueID(), player);
        final int dimensionId = player.worldObj.provider.dimensionId;
        Map<Integer, RtsWorkflowSlotManager> dimMap = playerSlots.get(player.getUniqueID());
        if (dimMap == null) {
            dimMap = new ConcurrentHashMap<Integer, RtsWorkflowSlotManager>();
            playerSlots.put(player.getUniqueID(), dimMap);
        }
        RtsWorkflowSlotManager slots = dimMap.get(dimensionId);
        if (slots == null) {
            slots = new RtsWorkflowSlotManager();
            dimMap.put(dimensionId, slots);
        }
        return slots;
    }

    private RtsWorkflowSlotManager getSlots(UUID playerId, int dimensionId) {
        Map<Integer, RtsWorkflowSlotManager> dimMap = playerSlots.get(playerId);
        return dimMap == null ? null : dimMap.get(dimensionId);
    }

    private EntityPlayerMP findPlayerByUUID(UUID playerId) {
        EntityPlayerMP cached = playerRefs.get(playerId);
        if (cached != null && cached.worldObj != null) return cached;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                if (obj instanceof EntityPlayerMP && playerId.equals(((EntityPlayerMP) obj).getUniqueID()))
                    return (EntityPlayerMP) obj;
            }
        }
        return null;
    }
}
