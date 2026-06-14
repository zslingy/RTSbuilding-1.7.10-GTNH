package com.rtsbuilding.rtsbuilding.server.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/**
 * RTS 共享进度数据 — WorldSavedData 实现。
 *
 * 完整迁移自原版 RtsSharedProgressionData（NeoForge 1.21 SavedData），适配 1.7.10 WorldSavedData。
 * 支持：
 * - 按 group key（团队标识符）分组存储已解锁进度节点
 * - 按 group key 存储家园锚点
 * - NBT 持久化保存/加载
 */
public final class RtsSharedProgressionData extends WorldSavedData {

    private static final String DATA_NAME = "rtsbuilding_shared_progression";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_GROUP = "group";
    private static final String KEY_UNLOCKED_NODES = "unlocked_nodes";
    private static final String KEY_HOME_POS_X = "home_pos_x";
    private static final String KEY_HOME_POS_Y = "home_pos_y";
    private static final String KEY_HOME_POS_Z = "home_pos_z";
    private static final String KEY_HOME_DIMENSION = "home_dimension";
    private static final String KEY_HOME_SET_GAME_TIME = "home_set_game_time";

    private final Map<String, SharedGroup> groups = new HashMap<>();

    public RtsSharedProgressionData(String name) {
        super(name);
    }

    public RtsSharedProgressionData() {
        this(DATA_NAME);
    }

    // ======== WorldSavedData 工厂 ========

    /**
     * 获取主世界上的共享进度数据实例（单例）。
     */
    public static RtsSharedProgressionData get(WorldServer overworld) {
        if (overworld == null) {
            return new RtsSharedProgressionData();
        }
        RtsSharedProgressionData data = (RtsSharedProgressionData) overworld.mapStorage
            .loadData(RtsSharedProgressionData.class, DATA_NAME);
        if (data == null) {
            data = new RtsSharedProgressionData();
            overworld.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ======== 公开 API ========

    /**
     * 获取指定团队的已解锁节点。
     */
    public LinkedHashSet<String> unlockedNodes(String groupKey) {
        if (groupKey == null || groupKey.isEmpty()) {
            return new LinkedHashSet<>();
        }
        SharedGroup group = getGroup(groupKey);
        return new LinkedHashSet<>(group.unlockedNodes);
    }

    /**
     * 保存指定团队的已解锁节点。
     */
    public void saveUnlockedNodes(String groupKey, Set<String> unlockedNodes) {
        if (groupKey == null || groupKey.isEmpty()) {
            return;
        }
        SharedGroup group = getGroup(groupKey);
        group.unlockedNodes.clear();
        group.unlockedNodes.addAll(unlockedNodes);
        markDirty();
    }

    /**
     * 获取指定团队的家园锚点。
     */
    public SharedHome home(String groupKey) {
        if (groupKey == null || groupKey.isEmpty()) {
            return null;
        }
        SharedGroup group = groups.get(groupKey);
        if (group == null || !group.hasHome) {
            return null;
        }
        return new SharedHome(
            group.homePosX,
            group.homePosY,
            group.homePosZ,
            group.homeDimension,
            group.homeSetGameTime);
    }

    /**
     * 设置指定团队的家园锚点。
     */
    public void setHome(String groupKey, int posX, int posY, int posZ, int dimensionId, long gameTime) {
        if (groupKey == null || groupKey.isEmpty()) {
            return;
        }
        SharedGroup group = getGroup(groupKey);
        group.homePosX = posX;
        group.homePosY = posY;
        group.homePosZ = posZ;
        group.homeDimension = dimensionId;
        group.homeSetGameTime = gameTime;
        group.hasHome = true;
        markDirty();
    }

    private SharedGroup getGroup(String groupKey) {
        SharedGroup existing = groups.get(groupKey);
        if (existing == null) {
            existing = new SharedGroup();
            groups.put(groupKey, existing);
        }
        return existing;
    }

    // ======== NBT 序列化 ========

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        groups.clear();
        NBTTagList groupList = tag.getTagList(KEY_GROUPS, 10); // TAG_COMPOUND = 10
        for (int i = 0; i < groupList.tagCount(); i++) {
            NBTTagCompound groupTag = groupList.getCompoundTagAt(i);
            String groupKey = groupTag.getString(KEY_GROUP);
            if (groupKey == null || groupKey.isEmpty()) {
                continue;
            }

            SharedGroup group = new SharedGroup();

            // 已解锁节点
            NBTTagList nodeList = groupTag.getTagList(KEY_UNLOCKED_NODES, 8); // TAG_STRING = 8
            for (int j = 0; j < nodeList.tagCount(); j++) {
                String nodeId = nodeList.getStringTagAt(j);
                if (nodeId != null && !nodeId.isEmpty()) {
                    group.unlockedNodes.add(nodeId);
                }
            }

            // 家园位置
            if (groupTag.hasKey(KEY_HOME_POS_X)) {
                group.homePosX = groupTag.getInteger(KEY_HOME_POS_X);
                group.homePosY = groupTag.getInteger(KEY_HOME_POS_Y);
                group.homePosZ = groupTag.getInteger(KEY_HOME_POS_Z);
                group.homeDimension = groupTag.getInteger(KEY_HOME_DIMENSION);
                group.homeSetGameTime = groupTag.getLong(KEY_HOME_SET_GAME_TIME);
                group.hasHome = true;
            }

            groups.put(groupKey, group);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList groupList = new NBTTagList();
        for (Map.Entry<String, SharedGroup> entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            SharedGroup group = entry.getValue();
            if (groupKey == null || groupKey.isEmpty()) {
                continue;
            }

            NBTTagCompound groupTag = new NBTTagCompound();
            groupTag.setString(KEY_GROUP, groupKey);

            // 已解锁节点
            NBTTagList nodeList = new NBTTagList();
            for (String nodeId : group.unlockedNodes) {
                if (nodeId != null && !nodeId.isEmpty()) {
                    nodeList.appendTag(new NBTTagString(nodeId));
                }
            }
            groupTag.setTag(KEY_UNLOCKED_NODES, nodeList);

            // 家园位置
            if (group.hasHome) {
                groupTag.setInteger(KEY_HOME_POS_X, group.homePosX);
                groupTag.setInteger(KEY_HOME_POS_Y, group.homePosY);
                groupTag.setInteger(KEY_HOME_POS_Z, group.homePosZ);
                groupTag.setInteger(KEY_HOME_DIMENSION, group.homeDimension);
                groupTag.setLong(KEY_HOME_SET_GAME_TIME, group.homeSetGameTime);
            }

            groupList.appendTag(groupTag);
        }
        tag.setTag(KEY_GROUPS, groupList);
    }

    // ======== 内部类型 ========

    public static final class SharedHome {

        public final int posX, posY, posZ;
        public final int dimensionId;
        public final long setGameTime;

        SharedHome(int posX, int posY, int posZ, int dimensionId, long setGameTime) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.dimensionId = dimensionId;
            this.setGameTime = setGameTime;
        }
    }

    private static final class SharedGroup {

        final LinkedHashSet<String> unlockedNodes = new LinkedHashSet<>();
        int homePosX, homePosY, homePosZ;
        int homeDimension;
        long homeSetGameTime;
        boolean hasHome;
    }
}
