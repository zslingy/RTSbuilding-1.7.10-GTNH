package com.rtsbuilding.rtsbuilding.server.progression;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStateMessage;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.progression.RtsUnlockEffect;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.util.RtsPlayerUtil;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * RTS 进度管理器（服务端）。
 *
 * 完整迁移自原版 RtsProgressionManager（NeoForge 1.21，566行），适配 1.7.10。
 * 负责：
 * - 进度开关与功能门控
 * - 家园锚点设置、冷却、范围检查
 * - 进度节点解锁（消耗材料、依赖检查）
 * - 共享进度团队支持
 * - NBT 持久化保存/加载
 * - 向客户端同步进度状态
 */
public final class RtsProgressionManager {

    // ---- 常量 ----
    public static final int DEFAULT_MAX_ACTION_RADIUS_BLOCKS = 128;
    public static final int DEFAULT_FLUID_CAPACITY_BUCKETS = 100;
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;
    public static final int HOME_SELECTION_RADIUS_CHUNKS = 1;
    public static final int HOME_RELOCATION_COOLDOWN_DAYS = 20;
    public static final long TICKS_PER_GAME_DAY = 24000L;
    public static final long HOME_RELOCATION_COOLDOWN_TICKS = HOME_RELOCATION_COOLDOWN_DAYS * TICKS_PER_GAME_DAY;

    // ---- NBT 键 ----
    private static final String NBT_ROOT = "rtsbuilding_progression";
    private static final String NBT_VERSION = "version";
    private static final String NBT_UNLOCKED_NODES = "unlocked_nodes";
    private static final String NBT_HOME_POS_X = "home_pos_x";
    private static final String NBT_HOME_POS_Y = "home_pos_y";
    private static final String NBT_HOME_POS_Z = "home_pos_z";
    private static final String NBT_HOME_DIMENSION = "home_dimension";
    private static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    // ---- 家园选择会话（服务端内存，不持久化） ----
    private static final ConcurrentMap<UUID, HomeSelection> HOME_SELECTIONS = new ConcurrentHashMap<>();

    private RtsProgressionManager() {}

    // ========================================================================
    // 公开 API：开关与门控
    // ========================================================================

    public static boolean isEnabled() {
        return Config.enableSurvivalProgression;
    }

    /**
     * 检查玩家是否可以使用指定功能。
     */
    public static boolean canUse(EntityPlayerMP player, RtsFeature feature) {
        if (!isEnabled()) {
            return true;
        }
        if (player == null || feature == null) {
            return false;
        }
        return derive(player).features.contains(feature);
    }

    public static double getActionRadius(EntityPlayerMP player) {
        if (!isEnabled()) {
            return Config.maxActionRadiusBlocks();
        }
        return Math.max(1.0D, derive(player).radiusBlocks);
    }

    public static int getFluidCapacityBuckets(EntityPlayerMP player) {
        if (!isEnabled()) {
            return DEFAULT_FLUID_CAPACITY_BUCKETS;
        }
        return Math.max(0, derive(player).fluidCapacityBuckets);
    }

    public static int getUltimineLimit(EntityPlayerMP player) {
        if (!isEnabled()) {
            return DEFAULT_ULTIMINE_LIMIT;
        }
        return Math.max(0, derive(player).ultimineLimit);
    }

    public static boolean canBypassHomeRadius(EntityPlayerMP player) {
        return !isEnabled() || derive(player).bypassHomeRadius;
    }

    // ========================================================================
    // 家园系统
    // ========================================================================

    public static boolean hasHome(EntityPlayerMP player) {
        return getHome(player) != null;
    }

    /**
     * 获取玩家的家园锚点。
     * 优先使用共享进度中的团队家园，否则使用个人家园。
     */
    public static HomeAnchor getHome(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isEmpty()) {
            RtsSharedProgressionData.SharedHome sharedHome = sharedProgressionData(player).home(sharedKey);
            if (sharedHome != null) {
                return new HomeAnchor(
                    sharedHome.posX,
                    sharedHome.posY,
                    sharedHome.posZ,
                    sharedHome.dimensionId,
                    sharedHome.setGameTime);
            }
        }
        return personalHome(player);
    }

    private static HomeAnchor personalHome(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }
        NBTTagCompound root = root(player);
        if (!root.hasKey(NBT_HOME_POS_X) || !root.hasKey(NBT_HOME_DIMENSION)) {
            return null;
        }
        return new HomeAnchor(
            root.getInteger(NBT_HOME_POS_X),
            root.getInteger(NBT_HOME_POS_Y),
            root.getInteger(NBT_HOME_POS_Z),
            root.getInteger(NBT_HOME_DIMENSION),
            root.getLong(NBT_HOME_SET_GAME_TIME));
    }

    /**
     * 检查坐标是否在家园操作范围内。
     */
    public static boolean canAccessHomeRadius(EntityPlayerMP player, int x, int z) {
        if (!isEnabled() || canBypassHomeRadius(player)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        HomeAnchor home = getHome(player);
        if (home == null || home.dimensionId != player.dimension) {
            return false;
        }
        double radius = getActionRadius(player);
        double dx = (x + 0.5D) - (home.posX + 0.5D);
        double dz = (z + 0.5D) - (home.posZ + 0.5D);
        double halfExtent = radius;
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }

    public static boolean canStartNormalRts(EntityPlayerMP player) {
        return !isEnabled() || hasHome(player);
    }

    public static boolean shouldStartHomeSelection(EntityPlayerMP player) {
        return isEnabled() && player != null && !hasHome(player) && canUse(player, RtsFeature.CAMERA);
    }

    public static void beginHomeSelection(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        int chunkX = (int) Math.floor(player.posX) >> 4;
        int chunkZ = (int) Math.floor(player.posZ) >> 4;
        HOME_SELECTIONS.put(RtsPlayerUtil.getUUID(player), new HomeSelection(player.dimension, chunkX, chunkZ));
    }

    public static void endHomeSelection(EntityPlayerMP player) {
        if (player != null) {
            HOME_SELECTIONS.remove(RtsPlayerUtil.getUUID(player));
        }
    }

    public static boolean isHomeSelectionActive(EntityPlayerMP player) {
        return player != null && HOME_SELECTIONS.containsKey(RtsPlayerUtil.getUUID(player));
    }

    /**
     * 检查坐标是否可以设置为家园（必须在选择范围内的 3×3 chunk 内）。
     */
    public static boolean canSelectHome(EntityPlayerMP player, int x, int z) {
        HomeSelection selection = player == null ? null : HOME_SELECTIONS.get(RtsPlayerUtil.getUUID(player));
        if (selection == null || selection.dimensionId != player.dimension) {
            return false;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return Math.abs(chunkX - selection.centerChunkX) <= HOME_SELECTION_RADIUS_CHUNKS
            && Math.abs(chunkZ - selection.centerChunkZ) <= HOME_SELECTION_RADIUS_CHUNKS;
    }

    public static boolean canChangeHome(EntityPlayerMP player) {
        if (!isEnabled()) {
            return true;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return true;
        }
        return unlockedNodes(player).contains(RtsProgressionNodes.FIELD_DEPLOYMENT)
            || remainingHomeCooldownTicks(player) <= 0L;
    }

    public static long remainingHomeCooldownTicks(EntityPlayerMP player) {
        if (!isEnabled() || player == null) {
            return 0L;
        }
        if (unlockedNodes(player).contains(RtsProgressionNodes.FIELD_DEPLOYMENT)) {
            return 0L;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return 0L;
        }
        long elapsed = Math.max(0L, player.worldObj.getTotalWorldTime() - home.setGameTime);
        return Math.max(0L, HOME_RELOCATION_COOLDOWN_TICKS - elapsed);
    }

    public static long remainingHomeCooldownDays(EntityPlayerMP player) {
        long ticks = remainingHomeCooldownTicks(player);
        return ticks <= 0L ? 0L : (ticks + TICKS_PER_GAME_DAY - 1L) / TICKS_PER_GAME_DAY;
    }

    /**
     * 提交家园位置。
     */
    public static boolean commitHome(EntityPlayerMP player, int posX, int posY, int posZ) {
        if (!isEnabled()) {
            return false;
        }
        if (player == null || !canSelectHome(player, posX, posZ)) {
            return false;
        }
        if (hasHome(player) && !canChangeHome(player)) {
            return false;
        }
        String sharedKey = sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            NBTTagCompound root = root(player);
            root.setInteger(NBT_VERSION, 1);
            root.setInteger(NBT_HOME_POS_X, posX);
            root.setInteger(NBT_HOME_POS_Y, posY);
            root.setInteger(NBT_HOME_POS_Z, posZ);
            root.setInteger(NBT_HOME_DIMENSION, player.dimension);
            root.setLong(NBT_HOME_SET_GAME_TIME, player.worldObj.getTotalWorldTime());
            player.getEntityData()
                .setTag(NBT_ROOT, root);
        } else {
            sharedProgressionData(player)
                .setHome(sharedKey, posX, posY, posZ, player.dimension, player.worldObj.getTotalWorldTime());
        }
        endHomeSelection(player);
        syncRelatedPlayers(player);
        return true;
    }

    // ========================================================================
    // 进度节点解锁
    // ========================================================================

    /**
     * 解锁一个进度节点。
     * 返回 UnlockResult 指示成功/失败及原因。
     */
    public static UnlockResult unlockNode(EntityPlayerMP player, ResourceLocation nodeId) {
        if (!isEnabled()) {
            return UnlockResult.disabledResult();
        }
        RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
        if (node == null) {
            return UnlockResult.failure("Unknown RTS node.");
        }
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        ensureStarterUnlocked(unlocked);
        if (unlocked.contains(nodeId)) {
            return UnlockResult.failure("Already unlocked.");
        }
        for (ResourceLocation dependency : node.getDependencies()) {
            if (!unlocked.contains(dependency)) {
                return UnlockResult.failure("Missing prerequisite.");
            }
        }
        List<RtsIngredientCost> costs = RtsProgressionNodes.costsFor(node);
        if (!hasCosts(player, costs)) {
            return UnlockResult.failure("Missing materials.");
        }
        consumeCosts(player, costs);
        unlocked.add(nodeId);
        saveUnlockedNodes(player, unlocked);
        syncRelatedPlayers(player);
        return UnlockResult.ok();
    }

    // ========================================================================
    // 玩家生命周期
    // ========================================================================

    public static void onPlayerLogin(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isEmpty() && sharedProgressionData(player).home(sharedKey) == null) {
            HomeAnchor personalHome = personalHome(player);
            if (personalHome != null) {
                sharedProgressionData(player).setHome(
                    sharedKey,
                    personalHome.posX,
                    personalHome.posY,
                    personalHome.posZ,
                    personalHome.dimensionId,
                    personalHome.setGameTime);
            }
        }
        if (ensureStarterUnlocked(unlocked) || !sharedKey.isEmpty()) {
            saveUnlockedNodes(player, unlocked);
        }
        syncToPlayer(player);
    }

    public static void onPlayerLogout(EntityPlayerMP player) {
        endHomeSelection(player);
    }

    // ========================================================================
    // 状态同步
    // ========================================================================

    /**
     * 向指定玩家发送完整进度状态。
     */
    public static void syncToPlayer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        List<String> costOverrides = new ArrayList<>();
        for (Map.Entry<String, String> entry : Config.progressionCostOverrides()
            .entrySet()) {
            costOverrides.add(entry.getKey() + "=" + entry.getValue());
        }
        if (!isEnabled()) {
            RtsNetworkManager.NETWORK.sendTo(
                new S2CRtsProgressionStateMessage(
                    false,
                    false,
                    0,
                    0,
                    0,
                    "",
                    0L,
                    Config.maxActionRadiusBlocks(),
                    DEFAULT_FLUID_CAPACITY_BUCKETS,
                    DEFAULT_ULTIMINE_LIMIT,
                    true,
                    new ArrayList<String>(),
                    new ArrayList<String>(),
                    costOverrides),
                player);
            return;
        }
        DerivedCapabilities derived = derive(player);
        HomeAnchor home = getHome(player);
        LinkedHashSet<ResourceLocation> unlockedSet = unlockedNodes(player);
        if (ensureStarterUnlocked(unlockedSet)) {
            saveUnlockedNodes(player, unlockedSet);
        }
        List<String> unlocked = new ArrayList<>();
        for (ResourceLocation id : unlockedSet) {
            unlocked.add(id.toString());
        }
        List<String> unlockable = new ArrayList<>();
        for (RtsProgressionNode node : RtsProgressionNodes.all()) {
            if (!unlockedSet.contains(node.getId()) && dependenciesMet(unlockedSet, node)
                && hasCosts(player, RtsProgressionNodes.costsFor(node))) {
                unlockable.add(
                    node.getId()
                        .toString());
            }
        }
        RtsNetworkManager.NETWORK.sendTo(
            new S2CRtsProgressionStateMessage(
                isEnabled(),
                home != null,
                home == null ? 0 : home.posX,
                home == null ? 0 : home.posY,
                home == null ? 0 : home.posZ,
                home == null ? "" : String.valueOf(home.dimensionId),
                remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                derived.fluidCapacityBuckets,
                derived.ultimineLimit,
                derived.bypassHomeRadius,
                unlocked,
                unlockable,
                costOverrides),
            player);
    }

    private static boolean dependenciesMet(Set<ResourceLocation> unlocked, RtsProgressionNode node) {
        for (ResourceLocation dependency : node.getDependencies()) {
            if (!unlocked.contains(dependency)) {
                return false;
            }
        }
        return true;
    }

    // ========================================================================
    // 能力推导
    // ========================================================================

    private static DerivedCapabilities derive(EntityPlayerMP player) {
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        ensureStarterUnlocked(unlocked);
        EnumSet<RtsFeature> features = EnumSet.noneOf(RtsFeature.class);
        int radius = 0;
        int fluidCapacity = 0;
        int ultimineLimit = 0;
        boolean bypassHome = false;
        for (ResourceLocation nodeId : unlocked) {
            RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
            if (node == null) {
                continue;
            }
            for (RtsUnlockEffect effect : node.getEffects()) {
                switch (effect.getType()) {
                    case UNLOCK_FEATURE:
                        if (effect.getFeature() != null) {
                            features.add(effect.getFeature());
                        }
                        break;
                    case SET_RADIUS_BLOCKS:
                        int radiusValue = RtsProgressionNodes.RADIUS_MAX.equals(node.getId())
                            ? Config.maxActionRadiusBlocks()
                            : effect.getValue();
                        radius = Math.max(radius, radiusValue);
                        break;
                    case SET_FLUID_CAPACITY_BUCKETS:
                        fluidCapacity = Math.max(fluidCapacity, effect.getValue());
                        break;
                    case SET_ULTIMINE_LIMIT:
                        ultimineLimit = Math.max(ultimineLimit, effect.getValue());
                        break;
                    case BYPASS_HOME_RADIUS:
                        bypassHome = true;
                        break;
                }
            }
        }
        return new DerivedCapabilities(features, radius <= 0 ? 16 : radius, fluidCapacity, ultimineLimit, bypassHome);
    }

    // ========================================================================
    // NBT 持久化
    // ========================================================================

    private static NBTTagCompound root(EntityPlayerMP player) {
        NBTTagCompound persistent = player.getEntityData();
        NBTTagCompound root = persistent.getCompoundTag(NBT_ROOT);
        if (root.hasNoTags()) {
            root.setInteger(NBT_VERSION, 1);
            persistent.setTag(NBT_ROOT, root);
        }
        return root;
    }

    private static LinkedHashSet<ResourceLocation> unlockedNodes(EntityPlayerMP player) {
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isEmpty()) {
            LinkedHashSet<String> rawShared = sharedProgressionData(player).unlockedNodes(sharedKey);
            LinkedHashSet<ResourceLocation> sharedUnlocked = new LinkedHashSet<>();
            for (String s : rawShared) {
                try {
                    sharedUnlocked.add(new ResourceLocation(s));
                } catch (Exception ignored) {}
            }
            sharedUnlocked.addAll(personalUnlockedNodes(player));
            sharedUnlocked.removeIf(id -> !RtsProgressionNodes.contains(id));
            return sharedUnlocked;
        }
        return personalUnlockedNodes(player);
    }

    private static LinkedHashSet<ResourceLocation> personalUnlockedNodes(EntityPlayerMP player) {
        NBTTagCompound root = root(player);
        LinkedHashSet<ResourceLocation> unlocked = new LinkedHashSet<>();
        NBTTagList list = root.getTagList(NBT_UNLOCKED_NODES, 8); // TAG_STRING = 8
        for (int i = 0; i < list.tagCount(); i++) {
            String raw = list.getStringTagAt(i);
            if (raw != null && !raw.isEmpty()) {
                try {
                    ResourceLocation id = new ResourceLocation(raw);
                    if (RtsProgressionNodes.contains(id)) {
                        unlocked.add(id);
                    }
                } catch (Exception ignored) {
                    // 无效的 ResourceLocation，跳过
                }
            }
        }
        return unlocked;
    }

    private static boolean ensureStarterUnlocked(Set<ResourceLocation> unlocked) {
        return unlocked.add(RtsProgressionNodes.CAMERA_CORE);
    }

    private static void saveUnlockedNodes(EntityPlayerMP player, Set<ResourceLocation> unlocked) {
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isEmpty()) {
            LinkedHashSet<ResourceLocation> sanitized = new LinkedHashSet<>();
            for (ResourceLocation id : unlocked) {
                if (RtsProgressionNodes.contains(id)) {
                    sanitized.add(id);
                }
            }
            Set<String> sanitizedStrings = new LinkedHashSet<>();
            for (ResourceLocation rl : sanitized) {
                sanitizedStrings.add(rl.toString());
            }
            sharedProgressionData(player).saveUnlockedNodes(sharedKey, sanitizedStrings);
            return;
        }
        NBTTagCompound root = root(player);
        NBTTagList list = new NBTTagList();
        for (ResourceLocation id : unlocked) {
            if (RtsProgressionNodes.contains(id)) {
                list.appendTag(new NBTTagString(id.toString()));
            }
        }
        root.setTag(NBT_UNLOCKED_NODES, list);
        player.getEntityData()
            .setTag(NBT_ROOT, root);
    }

    // ========================================================================
    // 共享进度（团队）
    // ========================================================================

    private static String sharedProgressionKey(EntityPlayerMP player) {
        if (!isEnabled() || player == null || !Config.shareSurvivalProgressionWithTeams) {
            return "";
        }
        // 香草 Scoreboard 团队
        ScorePlayerTeam team = player.worldObj.getScoreboard()
            .getPlayersTeam(player.getCommandSenderName());
        if (team != null) {
            return "scoreboard:" + team.getRegisteredName();
        }
        return "";
    }

    private static RtsSharedProgressionData sharedProgressionData(EntityPlayerMP player) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            overworld = (WorldServer) player.worldObj;
        }
        return RtsSharedProgressionData.get(overworld);
    }

    private static void syncRelatedPlayers(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        String sharedKey = sharedProgressionKey(player);
        if (sharedKey.isEmpty()) {
            syncToPlayer(player);
            return;
        }
        for (Object obj : player.mcServer.getConfigurationManager().playerEntityList) {
            EntityPlayerMP onlinePlayer = (EntityPlayerMP) obj;
            if (sharedKey.equals(sharedProgressionKey(onlinePlayer))) {
                syncToPlayer(onlinePlayer);
            }
        }
    }

    // ========================================================================
    // 材料消耗
    // ========================================================================

    private static boolean hasCosts(EntityPlayerMP player, List<RtsIngredientCost> costs) {
        for (RtsIngredientCost cost : costs) {
            Item item = getItemFromRL(cost.getItemId());
            if (item == null || countItem(player, item) < cost.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static int countItem(EntityPlayerMP player, Item item) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == item) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static void consumeCosts(EntityPlayerMP player, List<RtsIngredientCost> costs) {
        for (RtsIngredientCost cost : costs) {
            Item item = getItemFromRL(cost.getItemId());
            int remaining = cost.getCount();
            for (ItemStack stack : player.inventory.mainInventory) {
                if (remaining <= 0) {
                    break;
                }
                if (stack == null || stack.getItem() != item) {
                    continue;
                }
                int take = Math.min(remaining, stack.stackSize);
                stack.stackSize -= take;
                remaining -= take;
                if (stack.stackSize <= 0) {
                    stack.stackSize = 0;
                    // 清除空槽位由 Minecraft 自动处理
                }
            }
            // 也检查 armor 栏
            for (ItemStack stack : player.inventory.armorInventory) {
                if (remaining <= 0) {
                    break;
                }
                if (stack == null || stack.getItem() != item) {
                    continue;
                }
                int take = Math.min(remaining, stack.stackSize);
                stack.stackSize -= take;
                remaining -= take;
            }
        }
        player.inventory.markDirty();
    }

    private static Item getItemFromRL(ResourceLocation rl) {
        if (rl == null) {
            return null;
        }
        // 尝试 GameRegistry
        Item item = GameRegistry.findItem(rl.getResourceDomain(), rl.getResourcePath());
        if (item != null) {
            return item;
        }
        // 回退到 item registry
        Object obj = Item.itemRegistry.getObject(rl.toString());
        if (obj instanceof Item) {
            return (Item) obj;
        }
        return null;
    }

    // ========================================================================
    // 内部数据类型（替代原版 record）
    // ========================================================================

    public static final class HomeAnchor {

        public final int posX, posY, posZ;
        public final int dimensionId;
        public final long setGameTime;

        HomeAnchor(int posX, int posY, int posZ, int dimensionId, long setGameTime) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.dimensionId = dimensionId;
            this.setGameTime = setGameTime;
        }
    }

    private static final class HomeSelection {

        final int dimensionId;
        final int centerChunkX;
        final int centerChunkZ;

        HomeSelection(int dimensionId, int centerChunkX, int centerChunkZ) {
            this.dimensionId = dimensionId;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
        }
    }

    private static final class DerivedCapabilities {

        final EnumSet<RtsFeature> features;
        final int radiusBlocks;
        final int fluidCapacityBuckets;
        final int ultimineLimit;
        final boolean bypassHomeRadius;

        DerivedCapabilities(EnumSet<RtsFeature> features, int radiusBlocks, int fluidCapacityBuckets, int ultimineLimit,
            boolean bypassHomeRadius) {
            this.features = features;
            this.radiusBlocks = radiusBlocks;
            this.fluidCapacityBuckets = fluidCapacityBuckets;
            this.ultimineLimit = ultimineLimit;
            this.bypassHomeRadius = bypassHomeRadius;
        }
    }

    public static final class UnlockResult {

        public final boolean success;
        public final boolean disabled;
        public final String message;

        UnlockResult(boolean success, boolean disabled, String message) {
            this.success = success;
            this.disabled = disabled;
            this.message = message;
        }

        static UnlockResult ok() {
            return new UnlockResult(true, false, "");
        }

        static UnlockResult disabledResult() {
            return new UnlockResult(false, true, "Survival progression is disabled.");
        }

        static UnlockResult failure(String message) {
            return new UnlockResult(false, false, message);
        }

        public void notifyPlayer(EntityPlayerMP player) {
            if (!success && player != null && message != null && !message.isEmpty()) {
                player.addChatMessage(new ChatComponentText(message));
            }
        }
    }
}
