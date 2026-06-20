package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.InteractionViewModel.GuiBindingEntry;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.panel.fluid.FluidStripView;
import com.rtsbuilding.rtsbuilding.client.panel.pin.PinSlotView;
import com.rtsbuilding.rtsbuilding.client.panel.storage.RecentGridView;
import com.rtsbuilding.rtsbuilding.client.panel.storage.StorageCategoryView;
import com.rtsbuilding.rtsbuilding.client.panel.storage.StorageGridView;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;

import cpw.mods.fml.common.registry.GameData;

/**
 * 底部面板容器 — 统一管理底部区域的框架、标签栏、排序列、工具区、网格布局。
 *
 * 重构版本：对齐 RTSbuilding-main 的 BottomPanel 功能：
 * - 不可变布局结构 (BottomPanelLayout)
 * - 动态面板高度 (± 按钮，MIN/MAX 边界)
 * - 三标签页 (STORAGE / BLUEPRINTS / CREATIVE，创意模式动态显示)
 * - 刷新(R) + 引导(i) 头部按钮
 * - 钉选槽位分页器 + 物品计数覆盖
 * - 分类面板滚动箭头
 * - 存储网格空状态提示
 * - 右键交互 (物品 → 存流体 / 合成 → 数量对话框)
 *
 * 渲染顺序（对齐原版 BottomPanel.render()）：
 * 1. drawPanelFrame
 * 2. renderTabs
 * 3. renderRefreshGuideButtons
 * 4. sortButtons.render
 * 5. renderCraftDock
 * 6. categoryPanel.render
 * 7. searchBar.render + pager
 * 8. renderToolArea + renderPinSlots
 * 9. fluidStrip.render
 * 10. storageGrid.render
 * 11. recentGrid.render
 * 12. renderCraftablesPanel
 */
public class RtsBottomPanel implements IRtsPanel {

    public static final String PANEL_NAME = "bottom_panel";

    // ── 布局常量 ──
    static final int PANEL_PADDING = 8;
    static final int HEADER_H = 18;
    static final int SORT_COL_W = 58;
    static final int CATEGORY_W = 124;
    static final int CATEGORY_GAP = 5;
    static final int SLOT = 22;
    static final int HOTBAR_SLOT = 18;
    static final int HOTBAR_PITCH = 20;
    static final int TOOL_AREA_H = 18;
    static final int TOOL_HOTBAR_ITEM_SLOTS = 9;
    static final int EMPTY_HAND_BUTTON_INDEX = TOOL_HOTBAR_ITEM_SLOTS;
    static final int CRAFT_PANEL_W = 126;
    static final int CRAFT_PANEL_GAP = 6;
    static final int CRAFT_PANEL_COLS = 4;
    static final int CRAFT_PANEL_SLOT = 18;
    static final int CRAFT_PANEL_PITCH = 20;
    static final int CRAFT_PANEL_SEARCH_H = 12;
    static final int CRAFT_PANEL_APPLY_W = 18;
    static final int CRAFT_PANEL_TOGGLE_W = 38;
    static final int STORAGE_RECENT_GAP = 6;
    static final int FLUID_COLS = 2;
    static final int SEARCH_CLEAR_SIZE = 12;
    static final int SORT_BUTTON_SIZE = 16;
    static final int SORT_BUTTON_GAP = 4;
    static final int CATEGORY_ROW_H = 11;
    static final float CATEGORY_TEXT_SCALE = 0.84F;

    // 面板高度
    static final int MIN_PANEL_H = 72;
    static final int DEFAULT_PANEL_H = 110;
    static final int MAX_PANEL_H = 320;
    static final int MIN_STORAGE_GRID_ROWS = 2;

    // 合成底座
    private static final int CRAFT_DOCK_C_SIZE = 18;
    private static final int CRAFT_DOCK_SLOT_SIZE = 10;
    private static final int CRAFT_DOCK_GAP = 2;
    private static final int CRAFT_DOCK_SLOT_COUNT = 8;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();

    // ── 子面板引用（由 RtsScreen 注入）──
    private StorageGridView storageGrid;
    private StorageCategoryView categoryPanel;
    private RecentGridView recentGrid;
    private FluidStripView fluidStrip;

    // ── 内嵌子视图 ──
    private final SortButtonsView sortButtons;
    private final SearchBarView searchBar;
    private final PinSlotView pinSlots;

    // ── 标签页 ──
    private enum Tab {
        STORAGE,
        BLUEPRINTS,
        CREATIVE
    }

    private Tab activeTab = Tab.STORAGE;

    // ── 合成面板开关 ──
    private boolean craftPanelVisible = true;

    // ── 动态面板高度 ──
    private int panelHeight = DEFAULT_PANEL_H;

    // ── 钉选分页 ──
    private int pinPage = 0;

    // ── 分类滚动 ──
    private int categoryScroll = 0;

    // ── 拼合面板滚动 ──
    private int craftScroll = 0;

    // ── 布局缓存（每帧 render 时设置，供 getter 方法使用）──
    private BottomPanelLayout cachedLayout;

    // ── 帧级 hover 状态 ──
    private int hoveredTab = -1;
    private int hoveredToolSlot = -1;
    private boolean hoveredEmptyHandSlot = false;
    private int hoveredPinIndex = -1;
    private boolean hoveredPinPageButton = false;
    private boolean craftDockCHovered = false;
    private int hoveredCraftDockSlot = -1;
    private int hoveredCraftableEntry = -1;
    private boolean refreshHovered = false;
    private boolean guideHovered = false;

    public RtsBottomPanel() {
        this.state = RtsClientState.get();
        this.sortButtons = new SortButtonsView();
        this.searchBar = new SearchBarView();
        this.pinSlots = new PinSlotView();
    }

    /** 由 RtsScreen 注入子面板引用 */
    public void setSubPanels(StorageGridView storageGrid, StorageCategoryView categoryPanel, RecentGridView recentGrid,
        FluidStripView fluidStrip) {
        this.storageGrid = storageGrid;
        this.categoryPanel = categoryPanel;
        this.recentGrid = recentGrid;
        this.fluidStrip = fluidStrip;
    }

    // ======== IRtsPanel 协议 ========

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(
            0,
            Minecraft.getMinecraft().currentScreen.height - panelHeight,
            Minecraft.getMinecraft().currentScreen.width,
            panelHeight);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        int w = screen.width;
        int h = screen.height;

        // ── 计算面板高度边界（以 state.storage.panelHeight 为准，由 SortButtonsView ± 按钮修改）──
        int dynamicMaxH = Math.max(MIN_PANEL_H, Math.min(MAX_PANEL_H, h - 52 - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_PANEL_H, minHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.panelHeight = clampInt(state.storage.panelHeight, minH, dynamicMaxH);
        state.storage.panelHeight = this.panelHeight;

        // ── 计算不可变布局 ──
        cachedLayout = resolveLayout(w, h);

        // ── 1. 绘制面板框架 ──
        drawPanelFrame(screen, cachedLayout);

        // ── 2. 绘制标签栏 ──
        renderTabs(screen, cachedLayout, mouseX, mouseY);

        // ── 3. 刷新 + 引导按钮 ──
        renderRefreshGuideButtons(screen, cachedLayout, mouseX, mouseY);

        Tab active = resolveActiveTab();

        // ── 蓝图标签页 ──
        if (active == Tab.BLUEPRINTS) {
            renderBlueprintsContent(screen, cachedLayout, mouseX, mouseY);
            return;
        }

        // ── 4. 排序按钮列 ──
        sortButtons.render(screen, cachedLayout.sortX, cachedLayout.sortY, mouseX, mouseY);

        // ── 5. 合成底座 ──
        renderCraftDock(screen, cachedLayout, mouseX, mouseY);

        // ── 6. 分类面板 ──
        if (categoryPanel != null && categoryPanel.isVisible()) {
            categoryPanel.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 7. 搜索栏 + 分页器 ──
        searchBar.render(screen, cachedLayout.storageX, cachedLayout.storageY, cachedLayout.searchW, mouseX, mouseY);
        drawPager(screen, cachedLayout);

        // ── 8. 工具区 + 钉选槽位 ──
        renderToolArea(screen, cachedLayout, mouseX, mouseY);

        // ── 9. 流体条 ──
        if (fluidStrip != null && fluidStrip.isVisible()) {
            fluidStrip.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 10. 存储网格 ──
        if (storageGrid != null && storageGrid.isVisible()) {
            storageGrid.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 11. 最近使用网格 ──
        if (recentGrid != null && recentGrid.isVisible()) {
            recentGrid.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 12. 合成面板 ──
        if (craftPanelVisible) {
            renderCraftablesPanel(screen, cachedLayout, mouseX, mouseY);
        }
    }

    // ======== 布局计算 ========

    BottomPanelLayout resolveLayout(int screenW, int screenH) {
        int panelX = 0;
        int panelY = screenH - this.panelHeight;
        int panelW = screenW;
        int panelH = this.panelHeight;
        int contentX = PANEL_PADDING;
        int contentY = panelY + HEADER_H + 4;
        int sortX = contentX;
        int sortY = contentY + 2;
        int categoryX = sortX + SORT_COL_W;
        int categoryY = contentY;
        int categoryH = Math.max(24, panelY + panelH - PANEL_PADDING - categoryY);
        int storageX = categoryX + CATEGORY_W + 10;
        int storageY = contentY;
        int storageW = Math.max(120, panelW - PANEL_PADDING - storageX);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(72, mainStorageW - 82);
        int pagerX = Math.min(storageX + searchW + 4, craftPanelX - 80);
        searchW = Math.max(56, pagerX - storageX - 4);
        int toolY = storageY + 17;
        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, panelY + panelH - PANEL_PADDING - gridY);
        int storageRows = Math.max(1, gridH / SLOT);
        int craftPanelY = storageY;
        int craftPanelH = Math
            .max(CRAFT_PANEL_SEARCH_H + CRAFT_PANEL_SLOT + 27, panelY + panelH - PANEL_PADDING - craftPanelY);

        return new BottomPanelLayout(
            panelX,
            panelY,
            panelW,
            panelH,
            contentX,
            contentY,
            sortX,
            sortY,
            categoryX,
            categoryY,
            categoryH,
            storageX,
            storageY,
            storageW,
            craftPanelX,
            craftPanelY,
            craftPanelH,
            mainStorageW,
            searchW,
            pagerX,
            toolY,
            gridY,
            gridH,
            storageRows);
    }

    private static int minHeightForGridRows(int rows) {
        int gridTopOffset = HEADER_H + 4 + 17 + TOOL_AREA_H + 4;
        return gridTopOffset + PANEL_PADDING + Math.max(1, rows) * SLOT;
    }

    // ======== 框架绘制 ========

    private void drawPanelFrame(GuiScreen screen, BottomPanelLayout layout) {
        Gui.drawRect(
            layout.panelX,
            layout.panelY,
            layout.panelX + layout.panelW,
            layout.panelY + layout.panelH,
            0xD014151A);
        Gui.drawRect(
            layout.panelX + 1,
            layout.panelY + 1,
            layout.panelX + layout.panelW - 1,
            layout.panelY + HEADER_H,
            0xCC1C242F);
        Gui.drawRect(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + 1, 0xFF64788E);
        Gui.drawRect(
            layout.panelX,
            layout.panelY + layout.panelH - 1,
            layout.panelX + layout.panelW,
            layout.panelY + layout.panelH,
            0xFF0D1015);
        Gui.drawRect(layout.panelX, layout.panelY, layout.panelX + 1, layout.panelY + layout.panelH, 0xFF64788E);
        Gui.drawRect(
            layout.panelX + layout.panelW - 1,
            layout.panelY,
            layout.panelX + layout.panelW,
            layout.panelY + layout.panelH,
            0xFF0D1015);
    }

    // ======== 标签栏 ========

    private void renderTabs(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;

        fr.drawString("RTS", layout.panelX + 8, layout.panelY + 5, 0xFFF2F6FB);

        List<Tab> visibleTabs = visibleTabs();
        int tabX = layout.panelX + 38;
        hoveredTab = -1;

        for (int i = 0; i < visibleTabs.size(); i++) {
            Tab tab = visibleTabs.get(i);
            int tabW = tabWidth(tab);
            String label = tabLabel(tab);
            boolean active = resolveActiveTab() == tab;
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW
                && mouseY >= layout.panelY + 2
                && mouseY <= layout.panelY + HEADER_H - 3;

            if (hover) hoveredTab = i;

            int bgColor = active ? 0xCC355B4C : hover ? 0xAA334052 : 0x8826303B;
            int borderColor = active ? 0xFF7CCB93 : 0xFF536679;

            Gui.drawRect(tabX, layout.panelY + 2, tabX + tabW, layout.panelY + HEADER_H - 3, bgColor);
            Gui.drawRect(tabX, layout.panelY + 2, tabX + tabW, layout.panelY + 3, borderColor);

            fr.drawString(
                fr.trimStringToWidth(label, tabW - 8),
                tabX + tabW / 2 - fr.getStringWidth(label) / 2,
                layout.panelY + 4,
                active ? 0xFFFFFFFF : 0xFFD8E2EE);

            tabX += tabW + 4;
        }

        drawSelectedPlacementStatus(screen, layout, tabX);
    }

    private void renderRefreshGuideButtons(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;

        // 刷新按钮 (R) — 位于标签栏最右侧引导按钮左侧
        int refreshX = layout.panelX + layout.panelW - 36;
        int btnY = layout.panelY + 3;
        refreshHovered = inside(mouseX, mouseY, refreshX, btnY, 12, 12);
        boolean refreshDirty = resolveActiveTab() == Tab.STORAGE && state.storage.dirty;
        int refreshBg = refreshDirty ? (refreshHovered ? 0xDD2FAF49 : 0xCC248C3A)
            : (refreshHovered ? 0xCC41576F : 0xAA2B3542);
        Gui.drawRect(refreshX, btnY, refreshX + 12, btnY + 12, refreshBg);
        if (refreshDirty) {
            Gui.drawRect(refreshX, btnY, refreshX + 12, btnY + 1, 0xFF92F7A0);
            Gui.drawRect(refreshX, btnY + 11, refreshX + 12, btnY + 12, 0xFF92F7A0);
            Gui.drawRect(refreshX, btnY, refreshX + 1, btnY + 12, 0xFF92F7A0);
            Gui.drawRect(refreshX + 11, btnY, refreshX + 12, btnY + 12, 0xFF92F7A0);
        }
        fr.drawString("R", refreshX + 6 - fr.getStringWidth("R") / 2, btnY + 2, refreshDirty ? 0xFFFFFFFF : 0xFFEAF4FF);

        // 引导按钮 (i)
        int guideX = layout.panelX + layout.panelW - 20;
        guideHovered = inside(mouseX, mouseY, guideX, btnY, 12, 12);
        int guideBg = guideHovered ? 0xCC41576F : 0xAA2B3542;
        Gui.drawRect(guideX, btnY, guideX + 12, btnY + 12, guideBg);
        fr.drawString("i", guideX + 6 - fr.getStringWidth("i") / 2, btnY + 2, 0xFFEAF4FF);
    }

    private void drawSelectedPlacementStatus(GuiScreen screen, BottomPanelLayout layout, int startX) {
        FontRenderer fr = screen.mc.fontRenderer;
        int rightLimit = layout.panelX + layout.panelW - 60;
        if (startX >= rightLimit - 20) return;
        int maxW = Math.min(180, rightLimit - startX - 8);

        String statusText;
        int statusColor;
        if (state.interaction.selectedBlockId == null || state.interaction.selectedBlockId.isEmpty()
            || "minecraft:air".equals(state.interaction.selectedBlockId)) {
            statusText = tryTranslate("screen.rtsbuilding.status.selected_empty_hand", "Empty Hand");
            statusColor = 0xFFD8E2EE;
        } else {
            String itemName = state.interaction.selectedBlockId;
            int lastSlash = itemName.lastIndexOf(':');
            if (lastSlash >= 0 && lastSlash < itemName.length() - 1) {
                itemName = itemName.substring(lastSlash + 1);
            }
            statusText = tryTranslate("screen.rtsbuilding.status.selected_item", "Selected: ") + itemName;
            statusColor = 0xFFFCCB8A;
        }

        String trimmed = fr.trimStringToWidth(statusText, maxW);
        fr.drawString(trimmed, startX + 8, layout.panelY + 6, statusColor);
    }

    // ======== 分页器 ========

    private void drawPager(GuiScreen screen, BottomPanelLayout layout) {
        FontRenderer fr = screen.mc.fontRenderer;
        StorageViewModel svm = state.storage;
        boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();
        if (isSearchMode) return;

        int x = layout.pagerX;
        int y = layout.storageY;
        int page = svm.currentPage;
        int total = Math.max(1, svm.totalPages);

        Gui.drawRect(x, y, x + 16, y + 14, 0xAA2A2A2A);
        fr.drawString("<", x + 5, y + 3, 0xFFFFFFFF);
        Gui.drawRect(x + 58, y, x + 74, y + 14, 0xAA2A2A2A);
        fr.drawString(">", x + 63, y + 3, 0xFFFFFFFF);
        fr.drawString((page + 1) + "/" + total, x + 20, y + 3, 0xFFFFFFFF);
    }

    // ======== 合成底座 ========

    private void renderCraftDock(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;

        int dockX = layout.sortX;
        int dockY = layout.sortY + (SORT_BUTTON_SIZE + SORT_BUTTON_GAP) * 2;
        if (dockY + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_SLOT_SIZE * 2 + CRAFT_DOCK_GAP * 2
            > layout.panelY + layout.panelH - 2) return;

        int cX = dockX + 14;
        int cY = dockY + CRAFT_DOCK_SLOT_SIZE + CRAFT_DOCK_GAP;
        int cBg = craftDockCHovered ? 0xCC385465 : 0xAA24303A;
        Gui.drawRect(cX, cY, cX + CRAFT_DOCK_C_SIZE, cY + CRAFT_DOCK_C_SIZE, cBg);
        Gui.drawRect(cX, cY, cX + CRAFT_DOCK_C_SIZE, cY + 1, 0xFF6E8799);
        Gui.drawRect(cX, cY + CRAFT_DOCK_C_SIZE - 1, cX + CRAFT_DOCK_C_SIZE, cY + CRAFT_DOCK_C_SIZE, 0xFF111821);
        fr.drawString("C", cX + CRAFT_DOCK_C_SIZE / 2 - fr.getStringWidth("C") / 2, cY + 5, 0xFFFFFFFF);

        craftDockCHovered = inside(mouseX, mouseY, cX, cY, CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE);

        hoveredCraftDockSlot = -1;
        int ss = CRAFT_DOCK_SLOT_SIZE;
        int sg = CRAFT_DOCK_GAP;
        int cs = CRAFT_DOCK_C_SIZE;

        // 8 个绑定槽位：围绕 C 按钮的 3×3 网格（C 在中心）
        int[][] slotPositions = { { cX - ss - sg, cY - ss - sg }, { cX + (cs - ss) / 2, cY - ss - sg },
            { cX + cs + sg, cY - ss - sg }, { cX - ss - sg, cY + (cs - ss) / 2 }, { cX + cs + sg, cY + (cs - ss) / 2 },
            { cX - ss - sg, cY + cs + sg }, { cX + (cs - ss) / 2, cY + cs + sg }, { cX + cs + sg, cY + cs + sg } };

        InteractionViewModel ivm = state.interaction;
        for (int i = 0; i < CRAFT_DOCK_SLOT_COUNT; i++) {
            int sx = slotPositions[i][0];
            int sy = slotPositions[i][1];
            GuiBindingEntry binding = i < ivm.bindings.size() ? ivm.bindings.get(i) : null;
            boolean isBound = binding != null && binding.boundItemId != null && !binding.boundItemId.isEmpty();
            boolean isCapturing = ivm.guiBindingCaptureActive && ivm.guiBindingCaptureSlot == i;

            int bg;
            if (isCapturing) {
                bg = 0xCC2D6B47;
            } else if (isBound) {
                bg = 0xAA23384A;
            } else {
                bg = 0xAA202731;
            }
            if (inside(mouseX, mouseY, sx, sy, ss, ss)) {
                bg = isCapturing ? 0xDD377F53 : (isBound ? 0xBB2C4760 : 0xBB29323D);
                hoveredCraftDockSlot = i;
            }
            Gui.drawRect(sx, sy, sx + ss, sy + ss, bg);
            Gui.drawRect(sx, sy, sx + ss, sy + 1, 0xFF698097);
            Gui.drawRect(sx, sy + ss - 1, sx + ss, sy + ss, 0xFF0F151C);

            if (isBound) {
                ItemStack boundStack = state.storage.resolveStack(binding.boundItemId, binding.boundItemMeta);
                if (boundStack != null && boundStack.getItem() != null) {
                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_BLEND);
                    RenderHelper.enableGUIStandardItemLighting();
                    renderItem.renderItemAndEffectIntoGUI(fr, screen.mc.renderEngine, boundStack, sx + 1, sy + 1);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glPopMatrix();
                }
            } else {
                fr.drawString("+" + (i + 1), sx + 1, sy + 1, 0xFFB0B0B0);
            }
        }
    }

    // ======== 工具区 ========

    private void renderToolArea(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        Minecraft mc = screen.mc;
        FontRenderer fr = mc.fontRenderer;
        if (mc.thePlayer == null) return;

        int toolX = layout.storageX;
        int toolY = layout.toolY;
        hoveredToolSlot = -1;
        hoveredEmptyHandSlot = false;

        // 9 个快捷键栏
        for (int i = 0; i < TOOL_HOTBAR_ITEM_SLOTS; i++) {
            int cx = toolX + i * HOTBAR_PITCH;
            int cy = toolY;
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            boolean hasItem = stack != null && stack.getItem() != null;
            boolean selected = state.interaction.selectedToolSlot == i;

            int bgColor = selected ? 0xCC3A6E57 : (hasItem ? 0xAA1B1E25 : 0x881B1E25);
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bgColor);
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + 1, 0xFF5E6874);
            Gui.drawRect(cx, cy + HOTBAR_SLOT - 1, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            Gui.drawRect(cx, cy, cx + 1, cy + HOTBAR_SLOT, 0xFF5E6874);
            Gui.drawRect(cx + HOTBAR_SLOT - 1, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (hasItem) {
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_BLEND);
                float iconScale = 0.75f;
                float offset = (HOTBAR_SLOT - HOTBAR_SLOT * iconScale) / 2.0f;
                GL11.glTranslatef(cx + offset, cy + offset, 0);
                GL11.glScalef(iconScale, iconScale, 1.0f);
                RenderHelper.enableGUIStandardItemLighting();
                renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, stack, 1, 1);
                renderItem.renderItemOverlayIntoGUI(fr, mc.renderEngine, stack, 1, 1);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glPopMatrix();
            }

            if (inside(mouseX, mouseY, cx, cy, HOTBAR_SLOT, HOTBAR_SLOT)) {
                Gui.drawRect(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                hoveredToolSlot = i;
            }
        }

        // 空手按钮
        int emptyX = toolX + EMPTY_HAND_BUTTON_INDEX * HOTBAR_PITCH;
        int emptyY = toolY;
        boolean emptySelected = state.interaction.selectedBlockId == null || state.interaction.selectedBlockId.isEmpty()
            || "minecraft:air".equals(state.interaction.selectedBlockId);
        int emptyBg = emptySelected ? 0xCC9B604B : 0xB06F5146;
        Gui.drawRect(emptyX, emptyY, emptyX + HOTBAR_SLOT, emptyY + HOTBAR_SLOT, emptyBg);
        Gui.drawRect(emptyX, emptyY, emptyX + HOTBAR_SLOT, emptyY + 1, 0xFFFFD0B0);
        Gui.drawRect(emptyX, emptyY + HOTBAR_SLOT - 1, emptyX + HOTBAR_SLOT, emptyY + HOTBAR_SLOT, 0xFF0C0D10);
        fr.drawString("\u270B", emptyX + 2, emptyY + 3, 0xFFFFFFFF);

        if (inside(mouseX, mouseY, emptyX, emptyY, HOTBAR_SLOT, HOTBAR_SLOT)) {
            hoveredEmptyHandSlot = true;
        }

        // 钉选槽位（含分页器）
        int hotbarW = (EMPTY_HAND_BUTTON_INDEX + 1) * HOTBAR_PITCH;
        int pinStartX = toolX + hotbarW + 12;
        int pinEndX = layout.storageX + layout.mainStorageW;
        renderPinSlots(screen, pinStartX, toolY, mouseX, mouseY, pinEndX);
    }

    // ======== 钉选槽位（带分页器）=======

    private void renderPinSlots(GuiScreen screen, int startX, int rowY, int mouseX, int mouseY, int maxRight) {
        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;

        int visibleCells = (maxRight - startX) / HOTBAR_PITCH;
        if (visibleCells <= 0) return;

        int totalPins = PinSlotView.getMaxPins();
        boolean usePager = totalPins > visibleCells - 1;
        int slotsPerPage = usePager ? Math.max(1, visibleCells - 1) : visibleCells;
        int pageCount = Math.max(1, (totalPins + slotsPerPage - 1) / slotsPerPage);
        this.pinPage = clampInt(this.pinPage, 0, pageCount - 1);
        int startIdx = this.pinPage * slotsPerPage;

        hoveredPinIndex = -1;
        hoveredPinPageButton = false;

        for (int cell = 0; cell < visibleCells; cell++) {
            int cx = startX + cell * HOTBAR_PITCH;
            int cy = rowY;
            boolean pageButton = usePager && cell == visibleCells - 1;
            int pinIdx = startIdx + cell;
            boolean filled = !pageButton && pinIdx < totalPins && pinSlots.hasItem(pinIdx);

            int bg = pageButton ? 0xAA2C3A26 : (filled ? 0xAA253043 : 0xAA1A1A1A);
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + 1, 0xFF67758A);
            Gui.drawRect(cx, cy + HOTBAR_SLOT - 1, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            Gui.drawRect(cx, cy, cx + 1, cy + HOTBAR_SLOT, 0xFF67758A);
            Gui.drawRect(cx + HOTBAR_SLOT - 1, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (pageButton) {
                fr.drawString("+", cx + 6, cy + 5, 0xFFE9F7DA);
            } else if (pinIdx < totalPins) {
                ItemStack preview = pinSlots.getStack(pinIdx);
                if (preview != null && preview.getItem() != null) {
                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_BLEND);
                    RenderHelper.enableGUIStandardItemLighting();
                    renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, preview, cx + 1, cy + 1);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glPopMatrix();
                } else {
                    fr.drawString(
                        Integer.toString(pinIdx + 1),
                        cx + HOTBAR_SLOT / 2 - fr.getStringWidth(Integer.toString(pinIdx + 1)) / 2,
                        cy + 5,
                        0x88D0D8E4);
                }
            }

            if (inside(mouseX, mouseY, cx, cy, HOTBAR_SLOT, HOTBAR_SLOT)) {
                Gui.drawRect(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                if (pageButton) {
                    hoveredPinPageButton = true;
                } else if (pinIdx < totalPins) {
                    hoveredPinIndex = pinIdx;
                }
            }
        }
    }

    // ======== 合成面板 ========

    private void renderCraftablesPanel(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        if (state.craft.craftableEntries == null || state.craft.craftableEntries.isEmpty()) return;

        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;
        int x = layout.craftPanelX;
        int y = layout.craftPanelY;
        int h = layout.craftPanelH;

        // 面板背景
        Gui.drawRect(x, y, x + CRAFT_PANEL_W, y + h, 0xAA141922);

        // 标题
        Gui.drawRect(x, y, x + CRAFT_PANEL_W, y + HEADER_H, 0xAA1A1E2B);
        fr.drawString("Craft", x + 5, y + 4, 0xFFEAF2FF);

        // 搜索 + OK/ALL
        int searchY = y + HEADER_H + 1;
        int searchW = Math.max(24, CRAFT_PANEL_W - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int searchX = x + 4;
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;

        Gui.drawRect(searchX, searchY, searchX + searchW, searchY + CRAFT_PANEL_SEARCH_H, 0xAA1E2731);
        Gui.drawRect(searchX, searchY, searchX + searchW, searchY + 1, 0xFF5E738A);
        String searchDisplay = state.craft.craftSearchQuery.isEmpty() ? "..." : state.craft.craftSearchQuery;
        fr.drawString(fr.trimStringToWidth(searchDisplay, searchW - 4), searchX + 2, searchY + 2, 0xFFCCCCCC);

        int applyBg = 0xAA24303A;
        Gui.drawRect(applyX, searchY, applyX + CRAFT_PANEL_APPLY_W, searchY + CRAFT_PANEL_SEARCH_H, applyBg);
        Gui.drawRect(applyX, searchY, applyX + CRAFT_PANEL_APPLY_W, searchY + 1, 0xFF6E8799);
        fr.drawString("OK", applyX + 4, searchY + 2, 0xFFFFFFFF);

        int toggleBg = state.craft.craftShowAll ? 0xAA5A3D2A : 0xAA2C5A41;
        Gui.drawRect(toggleX, searchY, toggleX + CRAFT_PANEL_TOGGLE_W, searchY + CRAFT_PANEL_SEARCH_H, toggleBg);
        Gui.drawRect(toggleX, searchY, toggleX + CRAFT_PANEL_TOGGLE_W, searchY + 1, 0xFF667D95);
        fr.drawString(state.craft.craftShowAll ? "ALL" : "MAKE", toggleX + 4, searchY + 2, 0xFFFFFFFF);

        // 网格
        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int visibleRows = Math.max(1, (h - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<CraftableEntry> entries = filterCraftEntries(state.craft.craftableEntries);
        int totalRows = (entries.size() + CRAFT_PANEL_COLS - 1) / CRAFT_PANEL_COLS;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.craftScroll = clampInt(this.craftScroll, 0, maxScroll);
        int startIdx = this.craftScroll * CRAFT_PANEL_COLS;
        hoveredCraftableEntry = -1;

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < CRAFT_PANEL_COLS; col++) {
                int idx = startIdx + row * CRAFT_PANEL_COLS + col;
                int sx = x + 4 + col * CRAFT_PANEL_PITCH;
                int sy = gridY + row * CRAFT_PANEL_PITCH;

                int bg = 0xAA1A212B;
                if (idx < entries.size()) {
                    CraftableEntry entry = entries.get(idx);
                    bg = entry.craftableCount > 0 ? 0xAA214131 : 0xAA3F2323;
                }
                Gui.drawRect(sx, sy, sx + CRAFT_PANEL_SLOT, sy + CRAFT_PANEL_SLOT, bg);
                Gui.drawRect(sx, sy, sx + CRAFT_PANEL_SLOT, sy + 1, 0xFF596D84);
                Gui.drawRect(sx, sy + CRAFT_PANEL_SLOT - 1, sx + CRAFT_PANEL_SLOT, sy + CRAFT_PANEL_SLOT, 0xFF11171E);

                if (idx < entries.size()) {
                    CraftableEntry entry = entries.get(idx);
                    ItemStack stack = resolveCraftStack(entry);
                    if (stack != null) {
                        GL11.glPushMatrix();
                        GL11.glEnable(GL11.GL_BLEND);
                        RenderHelper.enableGUIStandardItemLighting();
                        renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, stack, sx + 1, sy + 1);
                        renderItem.renderItemOverlayIntoGUI(fr, mc.renderEngine, stack, sx + 1, sy + 1);
                        RenderHelper.disableStandardItemLighting();
                        GL11.glDisable(GL11.GL_BLEND);
                        GL11.glPopMatrix();

                        if (entry.craftableCount > 1) {
                            String countStr = compactCount(entry.craftableCount);
                            fr.drawString(
                                countStr,
                                sx + CRAFT_PANEL_SLOT - fr.getStringWidth(countStr) - 1,
                                sy + CRAFT_PANEL_SLOT - 9,
                                0xFFFFFFFF);
                        }
                    }
                    if (!isEntryCraftable(entry)) {
                        Gui.drawRect(sx + 1, sy + 1, sx + CRAFT_PANEL_SLOT - 1, sy + CRAFT_PANEL_SLOT - 1, 0x44220000);
                    }
                    if (inside(mouseX, mouseY, sx, sy, CRAFT_PANEL_SLOT, CRAFT_PANEL_SLOT)) {
                        Gui.drawRect(sx + 1, sy + 1, sx + CRAFT_PANEL_SLOT - 1, sy + CRAFT_PANEL_SLOT - 1, 0x22FFFFFF);
                        hoveredCraftableEntry = idx;
                    }
                }
            }
        }
    }

    private void renderBlueprintsContent(GuiScreen screen, BottomPanelLayout layout, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;
        int contentX = layout.panelX + PANEL_PADDING;
        int contentY = layout.panelY + HEADER_H + 4;
        String text = tryTranslate("screen.rtsbuilding.blueprints.placeholder", "Blueprints (coming soon)");
        fr.drawString(text, contentX + 4, contentY + 4, 0xFFAAAAAA);
    }

    // ======== 交互处理 ========

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (cachedLayout == null) return false;
        if (!cachedLayout.contains(mouseX, mouseY)) return false;

        Tab active = resolveActiveTab();

        // 头部标签栏点击（仅左键）
        if (cachedLayout.isInsideHeader(mouseX, mouseY)) {
            if (button == 0) {
                if (hoveredTab >= 0) {
                    List<Tab> visible = visibleTabs();
                    if (hoveredTab < visible.size()) {
                        activeTab = visible.get(hoveredTab);
                        toggleBlueprintPanel(activeTab == Tab.BLUEPRINTS);
                        return true;
                    }
                }
                if (refreshHovered) {
                    if (active == Tab.STORAGE) {
                        requestStoragePage(state.storage.currentPage);
                    }
                    return true;
                }
                if (guideHovered) {
                    return true;
                }
            }
            return true;
        }

        if (active == Tab.BLUEPRINTS) {
            return false;
        }

        if (active == Tab.CREATIVE && button != 0) {
            return true;
        }

        // 搜索栏（仅左键）
        if (button == 0 && searchBar.onMouseClick(mouseX, mouseY, button)) return true;

        // 分页器（仅左键）
        if (button == 0 && handlePagerClick(cachedLayout, mouseX, mouseY)) return true;

        // 排序按钮（仅左键）
        if (button == 0 && sortButtons.onMouseClick(mouseX, mouseY, button)) return true;

        // 合成底座（支持左右键）
        if (handleCraftDockClick(mouseX, mouseY, button, cachedLayout)) return true;

        // 合成面板（左键选中 + 右键选中并标记）
        if (craftPanelVisible && handleCraftPanelClick(cachedLayout, mouseX, mouseY, button)) return true;

        // 工具行（左键选物品、右键存流体/钉选）
        if (button == 0 && handleToolRowClick(cachedLayout, mouseX, mouseY, button)) return true;
        if (button == 1 && handleToolRowRightClick(cachedLayout, mouseX, mouseY)) return true;

        // 子面板
        if (storageGrid != null && storageGrid.isVisible()
            && storageGrid.getBounds()
                .contains(mouseX, mouseY)
            && storageGrid.onMouseClick(mouseX, mouseY, button)) return true;
        if (categoryPanel != null && categoryPanel.isVisible()
            && categoryPanel.getBounds()
                .contains(mouseX, mouseY)
            && categoryPanel.onMouseClick(mouseX, mouseY, button)) return true;
        if (recentGrid != null && recentGrid.isVisible()
            && recentGrid.getBounds()
                .contains(mouseX, mouseY)
            && recentGrid.onMouseClick(mouseX, mouseY, button)) return true;
        if (fluidStrip != null && fluidStrip.isVisible()
            && fluidStrip.getBounds()
                .contains(mouseX, mouseY)
            && fluidStrip.onMouseClick(mouseX, mouseY, button)) return true;

        return true; // 消费面板内所有点击
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (cachedLayout == null) return false;
        Tab active = resolveActiveTab();

        if (active != Tab.STORAGE) return false;

        // 合成面板滚轮
        if (inside(
            mouseX,
            mouseY,
            cachedLayout.craftPanelX,
            cachedLayout.craftPanelY,
            CRAFT_PANEL_W,
            cachedLayout.craftPanelH)) {
            List<CraftableEntry> entries = filterCraftEntries(state.craft.craftableEntries);
            int totalRows = (entries.size() + CRAFT_PANEL_COLS - 1) / CRAFT_PANEL_COLS;
            int visibleRows = Math
                .max(1, (cachedLayout.craftPanelH - (HEADER_H + 1 + CRAFT_PANEL_SEARCH_H + 6 + 6)) / CRAFT_PANEL_PITCH);
            int maxScroll = Math.max(0, totalRows - visibleRows);
            this.craftScroll = clampInt(this.craftScroll + (scroll > 0 ? -1 : 1), 0, maxScroll);
            return true;
        }

        // 搜索栏滚动 → 翻页
        if (mouseY >= cachedLayout.storageY && mouseY <= cachedLayout.storageY + SearchBarView.getTotalHeight()
            && mouseX >= cachedLayout.storageX
            && mouseX <= cachedLayout.storageX + cachedLayout.mainStorageW) {
            StorageViewModel svm = state.storage;
            if (!(svm.searchActive && !svm.searchQuery.isEmpty())) {
                if (scroll > 0 && svm.currentPage > 0) {
                    svm.currentPage--;
                    requestStoragePage(svm.currentPage);
                    return true;
                } else if (scroll < 0 && svm.currentPage < svm.totalPages - 1) {
                    svm.currentPage++;
                    requestStoragePage(svm.currentPage);
                    return true;
                }
            }
        }

        // 子面板滚轮
        if (storageGrid != null && storageGrid.isVisible()
            && storageGrid.getBounds()
                .contains(mouseX, mouseY))
            return storageGrid.onMouseScroll(mouseX, mouseY, scroll);
        if (categoryPanel != null && categoryPanel.isVisible()
            && categoryPanel.getBounds()
                .contains(mouseX, mouseY))
            return categoryPanel.onMouseScroll(mouseX, mouseY, scroll);
        if (fluidStrip != null && fluidStrip.isVisible()
            && fluidStrip.getBounds()
                .contains(mouseX, mouseY))
            return fluidStrip.onMouseScroll(mouseX, mouseY, scroll);

        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        if (resolveActiveTab() == Tab.STORAGE) {
            if (searchBar.onKeyTyped(c, keyCode)) return true;
            if (storageGrid != null) return storageGrid.onKeyTyped(c, keyCode);
        }
        return false;
    }

    @Override
    public void resetFrameState() {
        hoveredTab = -1;
        hoveredToolSlot = -1;
        hoveredEmptyHandSlot = false;
        hoveredPinIndex = -1;
        hoveredPinPageButton = false;
        craftDockCHovered = false;
        hoveredCraftDockSlot = -1;
        hoveredCraftableEntry = -1;
        refreshHovered = false;
        guideHovered = false;
        sortButtons.resetFrameState();
        searchBar.resetFrameState();
        pinSlots.resetFrameState();
    }

    // ======== 内部点击处理 ========

    private boolean handlePagerClick(BottomPanelLayout layout, int mouseX, int mouseY) {
        StorageViewModel svm = state.storage;
        if (svm.searchActive && !svm.searchQuery.isEmpty()) return false;
        int x = layout.pagerX;
        int y = layout.storageY;
        if (inside(mouseX, mouseY, x, y, 16, 14)) {
            if (svm.currentPage > 0) {
                svm.currentPage--;
                requestStoragePage(svm.currentPage);
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 58, y, 16, 14)) {
            if (svm.currentPage < svm.totalPages - 1) {
                svm.currentPage++;
                requestStoragePage(svm.currentPage);
            }
            return true;
        }
        return false;
    }

    private boolean handleCraftDockClick(int mouseX, int mouseY, int button, BottomPanelLayout layout) {
        if (craftDockCHovered && button == 0) {
            RtsNetworkManager.NETWORK
                .sendToServer(new com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalMessage());
            return true;
        }
        if (hoveredCraftDockSlot >= 0) {
            InteractionViewModel ivm = state.interaction;
            if (button == 0) {
                ivm.guiBindingCaptureActive = true;
                ivm.guiBindingCaptureSlot = hoveredCraftDockSlot;
            } else if (button == 1) {
                RtsNetworkManager.NETWORK.sendToServer(
                    new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingMessage(
                        (byte) hoveredCraftDockSlot));
            }
            return true;
        }
        return false;
    }

    private boolean handleToolRowClick(BottomPanelLayout layout, int mouseX, int mouseY, int button) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;

        int toolX = layout.storageX;
        int toolY = layout.toolY;
        if (!inside(mouseX, mouseY, toolX, toolY, layout.mainStorageW, TOOL_AREA_H)) return false;

        // 快捷键栏
        int hotbarX = toolX;
        int hotbarW = (TOOL_HOTBAR_ITEM_SLOTS + 1) * HOTBAR_PITCH;
        if (inside(mouseX, mouseY, hotbarX, toolY, hotbarW, HOTBAR_SLOT)) {
            int idx = (mouseX - hotbarX) / HOTBAR_PITCH;
            if (idx >= 0 && idx <= EMPTY_HAND_BUTTON_INDEX) {
                if (idx == EMPTY_HAND_BUTTON_INDEX) {
                    state.interaction.selectedBlockId = "";
                    state.interaction.selectedBlockMeta = 0;
                    state.interaction.selectedToolSlot = -1;
                } else {
                    ItemStack stack = mc.thePlayer.inventory.mainInventory[idx];
                    if (stack != null && stack.getItem() != null) {
                        String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                        state.interaction.selectedBlockId = itemId;
                        state.interaction.selectedBlockMeta = stack.getItemDamage();
                        state.interaction.selectedToolSlot = idx;
                    }
                }
                return true;
            }
        }

        // 钉选槽位（左键选中物品）
        int pinStartX = hotbarX + hotbarW + 12;
        int visibleCells = (layout.storageX + layout.mainStorageW - pinStartX) / HOTBAR_PITCH;
        if (visibleCells <= 0) return true;
        if (!inside(mouseX, mouseY, pinStartX, toolY, visibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) return true;

        int cell = (mouseX - pinStartX) / HOTBAR_PITCH;
        if (cell < 0 || cell >= visibleCells || mouseX > pinStartX + cell * HOTBAR_PITCH + HOTBAR_SLOT) return true;

        int totalPins = 8;
        boolean usePager = totalPins > visibleCells - 1;
        int slotsPerPage = usePager ? Math.max(1, visibleCells - 1) : visibleCells;
        int pageCount = Math.max(1, (totalPins + slotsPerPage - 1) / slotsPerPage);
        this.pinPage = clampInt(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == visibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIdx = this.pinPage * slotsPerPage + cell;
        if (pinIdx < 0 || pinIdx >= totalPins) return true;

        if (pinSlots.hasItem(pinIdx)) {
            ItemStack stack = pinSlots.getStack(pinIdx);
            if (stack != null && stack.getItem() != null) {
                String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                state.interaction.selectedBlockId = itemId;
                state.interaction.selectedBlockMeta = stack.getItemDamage();
                state.interaction.selectedToolSlot = -1;
            }
        }
        return true;
    }

    private boolean handleToolRowRightClick(BottomPanelLayout layout, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;

        int toolX = layout.storageX;
        int toolY = layout.toolY;
        if (!inside(mouseX, mouseY, toolX, toolY, layout.mainStorageW, TOOL_AREA_H)) return false;

        int hotbarX = toolX;
        int hotbarW = (TOOL_HOTBAR_ITEM_SLOTS + 1) * HOTBAR_PITCH;

        // 快捷键栏右键 → 存储流体
        if (inside(mouseX, mouseY, hotbarX, toolY, hotbarW, HOTBAR_SLOT)) {
            int idx = (mouseX - hotbarX) / HOTBAR_PITCH;
            if (idx >= 0 && idx < TOOL_HOTBAR_ITEM_SLOTS) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[idx];
                if (stack != null && stack.getItem() != null) {
                    RtsNetworkManager.NETWORK.sendToServer(
                        new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotMessage(idx, 1));
                }
                return true;
            }
        }

        // 钉选槽位右键 → 钉选当前选中物品
        int pinStartX = hotbarX + hotbarW + 12;
        int visibleCells = (layout.storageX + layout.mainStorageW - pinStartX) / HOTBAR_PITCH;
        if (visibleCells <= 0) return true;
        if (!inside(mouseX, mouseY, pinStartX, toolY, visibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) return true;

        int cell = (mouseX - pinStartX) / HOTBAR_PITCH;
        if (cell < 0 || cell >= visibleCells || mouseX > pinStartX + cell * HOTBAR_PITCH + HOTBAR_SLOT) return true;

        int totalPins = 8;
        boolean usePager = totalPins > visibleCells - 1;
        int slotsPerPage = usePager ? Math.max(1, visibleCells - 1) : visibleCells;
        int pageCount = Math.max(1, (totalPins + slotsPerPage - 1) / slotsPerPage);
        this.pinPage = clampInt(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == visibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIdx = this.pinPage * slotsPerPage + cell;
        if (pinIdx < 0 || pinIdx >= totalPins) return true;

        if (state.interaction.selectedBlockId != null && !state.interaction.selectedBlockId.isEmpty()
            && !"minecraft:air".equals(state.interaction.selectedBlockId)) {
            pinSlots.pinItem(pinIdx, state.interaction.selectedBlockId, state.interaction.selectedBlockMeta);
        }
        return true;
    }

    private boolean handleCraftPanelClick(BottomPanelLayout layout, int mouseX, int mouseY, int button) {
        if (state.craft.craftableEntries == null || state.craft.craftableEntries.isEmpty()) return false;

        int x = layout.craftPanelX;
        int y = layout.craftPanelY;
        if (!inside(mouseX, mouseY, x, y, CRAFT_PANEL_W, layout.craftPanelH)) return false;

        int searchY = y + HEADER_H + 1;
        int searchW = Math.max(24, CRAFT_PANEL_W - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int searchX = x + 4;
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;

        // ALL/MAKE 切换 + OK（仅左键）
        if (button == 0) {
            if (inside(mouseX, mouseY, toggleX, searchY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H)) {
                state.craft.craftShowAll = !state.craft.craftShowAll;
                state.craft.craftScroll = 0;
                return true;
            }
            if (inside(mouseX, mouseY, applyX, searchY, CRAFT_PANEL_APPLY_W, CRAFT_PANEL_SEARCH_H)) {
                state.craft.craftScroll = 0;
                return true;
            }
        }

        // 搜索框区域（不消费右键）
        if (inside(mouseX, mouseY, searchX, searchY, searchW, CRAFT_PANEL_SEARCH_H)) {
            return button == 0;
        }

        // 网格点击（左右键均可选中物品）
        if (hoveredCraftableEntry >= 0) {
            List<CraftableEntry> entries = filterCraftEntries(state.craft.craftableEntries);
            if (hoveredCraftableEntry < entries.size()) {
                CraftableEntry entry = entries.get(hoveredCraftableEntry);
                state.interaction.selectedBlockId = entry.itemId;
                state.interaction.selectedBlockMeta = entry.meta;
                state.interaction.addRecentBlock(entry.itemId);
            }
            return true;
        }
        return true;
    }

    // ======== Getter 方法（供子面板使用）=======

    public int getPanelX() {
        return cachedLayout != null ? cachedLayout.panelX : 0;
    }

    public int getPanelY() {
        return cachedLayout != null ? cachedLayout.panelY : 0;
    }

    public int getPanelW() {
        return cachedLayout != null ? cachedLayout.panelW : 0;
    }

    public int getPanelH() {
        return cachedLayout != null ? cachedLayout.panelH : 0;
    }

    public int getCategoryX() {
        return cachedLayout != null ? cachedLayout.categoryX : 0;
    }

    public int getCategoryY() {
        return cachedLayout != null ? cachedLayout.categoryY : 0;
    }

    public int getStorageX() {
        return cachedLayout != null ? cachedLayout.storageX : 0;
    }

    public int getStorageY() {
        return cachedLayout != null ? cachedLayout.gridY : 0;
    }

    public int getStorageW() {
        if (cachedLayout == null) return 100;
        int main = cachedLayout.mainStorageW;
        return Math.max(SLOT, (main - STORAGE_RECENT_GAP) / 2);
    }

    public int getGridH() {
        return cachedLayout != null ? cachedLayout.gridH : 100;
    }

    public int getFluidStripX() {
        return cachedLayout != null ? cachedLayout.storageX : 0;
    }

    public int getFluidStripW() {
        return FLUID_COLS * SLOT;
    }

    public int getRecentX() {
        if (cachedLayout == null) return 0;
        return cachedLayout.storageX + getStorageW() + STORAGE_RECENT_GAP;
    }

    public int getRecentY() {
        return cachedLayout != null ? cachedLayout.gridY : 0;
    }

    public int getRecentW() {
        if (cachedLayout == null) return 100;
        return Math.max(SLOT, cachedLayout.mainStorageW - getStorageW() - STORAGE_RECENT_GAP);
    }

    public int getCraftX() {
        return cachedLayout != null ? cachedLayout.craftPanelX : 0;
    }

    public int getCraftY() {
        return cachedLayout != null ? cachedLayout.craftPanelY : 0;
    }

    public int getCraftW() {
        return CRAFT_PANEL_W;
    }

    public int getCraftH() {
        return cachedLayout != null ? cachedLayout.craftPanelH : 0;
    }

    public boolean isStorageTab() {
        return resolveActiveTab() == Tab.STORAGE;
    }

    /** 每 tick 更新搜索框光标闪烁 */
    public void updateSearchCursor() {
        searchBar.updateCursorCounter();
    }

    public boolean isSearchFocused() {
        return searchBar.isFocused();
    }

    // ======== 标签页管理 ========

    private List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        boolean isCreative = Minecraft.getMinecraft().thePlayer != null
            && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode;
        if (isCreative) tabs.add(Tab.CREATIVE);
        tabs.add(Tab.STORAGE);
        tabs.add(Tab.BLUEPRINTS);
        return tabs;
    }

    private Tab resolveActiveTab() {
        Tab tab = this.activeTab;
        if (tab == Tab.CREATIVE) {
            boolean isCreative = Minecraft.getMinecraft().thePlayer != null
                && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode;
            if (!isCreative) tab = Tab.STORAGE;
        }
        return tab;
    }

    private int tabWidth(Tab tab) {
        if (tab == Tab.CREATIVE) return 58;
        if (tab == Tab.STORAGE) return 76;
        return 86;
    }

    private String tabLabel(Tab tab) {
        switch (tab) {
            case CREATIVE:
                return tryTranslate("screen.rtsbuilding.creative.tab", "CREATIVE");
            case BLUEPRINTS:
                return tryTranslate("screen.rtsbuilding.blueprints.tab", "BLUEPRINTS");
            default:
                return tryTranslate("screen.rtsbuilding.storage.tab", "STORAGE");
        }
    }

    private void toggleBlueprintPanel(boolean show) {
        GuiScreen scr = Minecraft.getMinecraft().currentScreen;
        if (scr instanceof RtsScreen) {
            IRtsPanel bpPanel = ((RtsScreen) scr).getPanel("blueprint_panel");
            if (bpPanel instanceof BlueprintPanel) {
                BlueprintPanel bp = (BlueprintPanel) bpPanel;
                if (show && !bp.isVisible()) bp.toggleVisibility();
                else if (!show && bp.isVisible()) bp.toggleVisibility();
            }
        }
    }

    // ======== 内部辅助 ========

    private BottomPanelLayout resolveLayoutScreen() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null) screen = new GuiScreen() {};
        return resolveLayout(screen.width, screen.height);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clampInt(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private boolean hasFluidItems() {
        StorageViewModel svm = state.storage;
        for (StorageViewModel.StorageEntry e : svm.entries) {
            if (e != null && e.itemId != null
                && (e.itemId.contains("bucket") || e.itemId.contains("bottle")
                    || e.itemId.contains("potion")
                    || e.itemId.contains("fluid")))
                return true;
        }
        return false;
    }

    private void requestStoragePage(int page) {
        RtsNetworkManager.NETWORK.sendToServer(
            new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage(
                page,
                0,
                state.storage.sortMode));
    }

    private List<CraftableEntry> filterCraftEntries(List<CraftableEntry> entries) {
        if (entries == null || entries.isEmpty()) return entries;
        String q = state.craft.craftSearchQuery.trim()
            .toLowerCase();
        if (q.isEmpty()) {
            if (!state.craft.craftShowAll) {
                List<CraftableEntry> filtered = new ArrayList<>();
                for (CraftableEntry e : entries) {
                    if (isEntryCraftable(e)) filtered.add(e);
                }
                return filtered;
            }
            return entries;
        }
        List<CraftableEntry> filtered = new ArrayList<>();
        for (CraftableEntry e : entries) {
            if (e.displayName != null && e.displayName.toLowerCase()
                .contains(q)) {
                if (state.craft.craftShowAll || isEntryCraftable(e)) filtered.add(e);
            }
        }
        return filtered;
    }

    private boolean isEntryCraftable(CraftableEntry entry) {
        if (entry == null || entry.ingredients.isEmpty()) return false;
        for (com.rtsbuilding.rtsbuilding.client.CraftViewModel.IngredientSlot ing : entry.ingredients) {
            if (!ing.isSatisfied()) return false;
        }
        return true;
    }

    private ItemStack resolveCraftStack(CraftableEntry entry) {
        if (entry == null || entry.itemId == null) return null;
        try {
            ResourceLocation rl = new ResourceLocation(entry.itemId);
            net.minecraft.item.Item item = (net.minecraft.item.Item) GameData.getItemRegistry()
                .getObject(rl);
            if (item != null) return new ItemStack(item, 1, entry.meta);
        } catch (Exception ignored) {}
        return null;
    }

    private static String compactCount(int count) {
        if (count >= 1000000) return (count / 1000000) + "M";
        if (count >= 1000) return (count / 1000) + "K";
        return Integer.toString(count);
    }

    private static String tryTranslate(String key, String fallback) {
        String s = StatCollector.translateToLocal(key);
        return (s == null || s.equals(key)) ? fallback : s;
    }
}
