package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.client.panel.quickbuild.ShapeBuildSession;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;

/**
 * 交互 ViewModel — 管理客户端交互模式状态。
 * 
 * 从原 ClientRtsController 中拆出，负责：
 * - 当前 BuilderMode（INTERACT / LINK_STORAGE / FUNNEL / ROTATE）
 * - 建造/采矿/破坏子模式
 * - 快速建造参数
 * - GUI 绑定状态
 */
public class InteractionViewModel {

    public static final int QUICK_SLOT_COUNT = 27;
    // ---- 模式 ----
    public BuilderMode currentMode = BuilderMode.INTERACT; // Bug2修复：对齐原版默认 INTERACT，否则右键交互无响应

    // ---- 建造 ----
    public String selectedBlockId = "";
    public int selectedBlockMeta = 0;

    // ---- 快速建造 ----
    public boolean quickBuildActive = false;
    public String quickBuildShape = "block"; // block, line, square, wall, circle, box
    public String quickBuildFill = "fill"; // fill, hollow, skeleton
    public int quickBuildSizeX = 3;
    public int quickBuildSizeY = 3;
    public int quickBuildSizeZ = 3;
    public int quickBuildRotation = 0; // 0-23 (15° 为单位)
    public boolean quickBuildCylinder = false; // CIRCLE 形状的圆柱模式
    public boolean lineSnap8Direction = false; // LINE/WALL 8向角度吸附开关
    /** 当前形状建造会话（锚点 A/B、阶段、旋转等），由交互处理器设置 */
    public ShapeBuildSession shapeBuildSession = null;

    // ---- 采矿 ----
    public boolean miningActive = false;
    public String miningSize = "1x1"; // 1x1, 3x3, 5x5

    // ---- 范围破坏 ----
    public boolean areaDestroyActive = false;
    public int areaDestroySizeX = 3;
    public int areaDestroySizeY = 3;
    public int areaDestroySizeZ = 3;

    // ---- 绑定 ----
    public final List<GuiBindingEntry> bindings = new ArrayList<>();
    public boolean bindingEditMode = false;
    public boolean guiBindingCaptureActive = false;
    public int guiBindingCaptureSlot = -1;

    // Bug9修复：快捷存储栏（Pin 槽），27 格对标原版 QUICK_SLOT_COUNT
    public final ItemStack[] quickSlotItems = new ItemStack[QUICK_SLOT_COUNT];

    // ---- 漏斗模式 ----
    public boolean funnelActive = false;
    public String funnelTargetItemId = "";
    public static final int FUNNEL_MIN_RANGE = 1;
    public static final int FUNNEL_MAX_RANGE = 16;
    public int funnelRangeSize = 5;
    public boolean funnelHasTarget = false;
    public double funnelTargetX = 0.0D;
    public double funnelTargetY = 0.0D;
    public double funnelTargetZ = 0.0D;

    // ---- Bug1修复：顶栏功能按钮状态 ----
    /** 连锁挖掘模式是否激活 */
    public boolean ultimineActive = false;
    /** 连锁挖掘上限（每次最多连锁多少方块） */
    public int ultimineLimit = 64;
    /** 区块边框显示是否激活 */
    public boolean chunkViewActive = false;

    // ---- Bug4修复：挖掘进度追踪（服务端 S2CRtsMineProgressMessage → 客户端） ----
    /** 当前挖掘目标 X 坐标（-1 表示无活跃挖掘） */
    public int mineProgressX = -1;
    public int mineProgressY = -1;
    public int mineProgressZ = -1;
    /** 挖掘进度阶段（0-9 = 破坏阶段, 10 = 完成） */
    public byte mineProgressStage = 0;

    // ---- 连锁挖掘进度追踪（服务端 S2CRtsUltimineProgressMessage → 客户端） ----
    /** 连锁挖掘已处理方块数（-1 表示无活跃连锁） */
    public int ultimineProgressProcessed = -1;
    /** 连锁挖掘总方块数 */
    public int ultimineProgressTotal = 0;

    // ---- Bug1修复：GearMenuPanel 设置项状态 ----
    public boolean autoStoreMinedDrops = true;
    public boolean startCameraAtPlayerHead = false;
    public boolean allowPlacedBlockRecovery = false;
    public boolean debugButtonVisible = false;
    public boolean containerOverlayEnabled = true;
    public boolean shiftImportEnabled = true;
    public boolean invertPanDragX = false;
    public boolean invertPanDragY = false;
    public boolean smoothCamera = true;
    public boolean damageSoundEnabled = true;
    public boolean damageAutoReturnEnabled = true;
    public boolean bdNetworkEnabled = false;

    // ---- 最近使用的方块 ----
    public final List<String> recentBlocks = new ArrayList<>();
    public static final int MAX_RECENT_BLOCKS = 40;

    /** GUI 绑定条目 */
    public static class GuiBindingEntry {

        public String keyName;
        public String actionName;
        public String boundItemId;
        public int boundItemMeta;

        public GuiBindingEntry(String keyName, String actionName, String boundItemId, int boundItemMeta) {
            this.keyName = keyName;
            this.actionName = actionName;
            this.boundItemId = boundItemId;
            this.boundItemMeta = boundItemMeta;
        }
    }

    public void addRecentBlock(String blockId) {
        recentBlocks.remove(blockId);
        recentBlocks.add(0, blockId);
        while (recentBlocks.size() > MAX_RECENT_BLOCKS) {
            recentBlocks.remove(recentBlocks.size() - 1);
        }
    }

    public void resetFrameState() {
        // 清除瞬时标志等
    }

    /** Bug9修复：获取指定 Pin 槽的物品 */
    public ItemStack getQuickSlotItem(int index) {
        return (index >= 0 && index < QUICK_SLOT_COUNT) ? quickSlotItems[index] : null;
    }

    public void resetForNewSession() {
        currentMode = BuilderMode.OFF;
        selectedBlockId = "";
        selectedBlockMeta = 0;
        quickBuildActive = false;
        quickBuildShape = "block";
        quickBuildFill = "fill";
        quickBuildSizeX = 3;
        quickBuildSizeY = 3;
        quickBuildSizeZ = 3;
        quickBuildRotation = 0;
        quickBuildCylinder = false;
        lineSnap8Direction = false;
        shapeBuildSession = null;
        miningActive = false;
        areaDestroyActive = false;
        bindingEditMode = false;
        guiBindingCaptureActive = false;
        guiBindingCaptureSlot = -1;
        funnelActive = false;
        funnelTargetItemId = "";
        funnelRangeSize = 5;
        funnelHasTarget = false;
        funnelTargetX = 0.0D;
        funnelTargetY = 0.0D;
        funnelTargetZ = 0.0D;
        bindings.clear();
        recentBlocks.clear();
        autoStoreMinedDrops = true;
        startCameraAtPlayerHead = false;
        allowPlacedBlockRecovery = false;
        debugButtonVisible = false;
        containerOverlayEnabled = true;
        shiftImportEnabled = true;
        invertPanDragX = false;
        invertPanDragY = false;
        smoothCamera = true;
        damageSoundEnabled = true;
        damageAutoReturnEnabled = true;
        bdNetworkEnabled = false;
        mineProgressX = -1;
        mineProgressY = -1;
        mineProgressZ = -1;
        mineProgressStage = 0;
        ultimineProgressProcessed = -1;
        ultimineProgressTotal = 0;
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            quickSlotItems[i] = null;
        }
    }
}
