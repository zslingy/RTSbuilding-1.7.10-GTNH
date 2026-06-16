package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;
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
 * 渲染顺序（对齐原版 BottomPanel.render()）：
 * 1. drawPanelFrame
 * 2. renderTabs
 * 3. sortButtons.render
 * 4. renderCraftDock
 * 5. categoryPanel.render
 * 6. searchBar.render
 * 7. renderToolArea + renderPinSlots
 * 8. fluidStrip.render
 * 9. storageGrid.render
 * 10. recentGrid.render
 * 11. renderCraftablesPanel
 */
public class RtsBottomPanel implements IRtsPanel {

    public static final String PANEL_NAME = "bottom_panel";

    // ── 布局常量 ──
    static final int PANEL_PADDING = 6;
    static final int HEADER_H = 14;
    static final int SORT_COL_W = 44;
    static final int CATEGORY_W = 80;
    static final int CATEGORY_GAP = 5;
    static final int SLOT = 14; // Issue 5: 与快捷栏格子大小一致
    static final int HOTBAR_SLOT = 14;
    static final int HOTBAR_PITCH = 15;
    static final int TOOL_AREA_H = 14;
    static final int CRAFT_PANEL_W = 95;
    static final int CRAFT_PANEL_GAP = 5;
    static final int STORAGE_RECENT_GAP = 5;
    static final int FLUID_COLS = 2;
    static final int ICON_SCALE = 0; // Issue 5: 物品图标缩放标记（0.75x通过GL实现）

    // 合成底座常量
    private static final int CRAFT_DOCK_C_SIZE = 14;
    private static final int CRAFT_DOCK_SLOT_SIZE = 8;
    private static final int CRAFT_DOCK_GAP = 2;
    private static final int CRAFT_DOCK_SLOT_COUNT = 8;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();

    // ── 子面板引用（由 RtsScreen 注入） ──
    private StorageGridView storageGrid;
    private StorageCategoryView categoryPanel;
    private RecentGridView recentGrid;
    private FluidStripView fluidStrip;

    // ── 内嵌子视图（由本面板拥有） ──
    private final SortButtonsView sortButtons;
    private final SearchBarView searchBar;
    private final PinSlotView pinSlots;

    // ── 合成面板开关 ──
    private boolean craftPanelVisible = false;

    // ── 布局缓存 ──
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY;
    private int categoryX, categoryY, categoryH;
    private int storageX, storageY, storageW;
    private int searchBarY;
    private int toolY;
    private int gridY, gridH;
    private int fluidStripX, fluidStripW;
    private int storageGridX, storageGridW;
    private int recentX, recentY, recentW;
    private int craftPanelX, craftPanelY, craftPanelH;

    // ── 标签页 ──
    private enum Tab {
        STORAGE,
        BLUEPRINTS
    }

    private Tab activeTab = Tab.STORAGE;
    private int hoveredTab = -1;

    // ── 工具区 hover ──
    private int hoveredToolSlot = -1;

    // ── 合成底座 hover ──
    private boolean craftDockCHovered = false;
    private int hoveredCraftDockSlot = -1;

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
        return new Rectangle(panelX, panelY, panelW, panelH);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;
        int w = screen.width;
        int h = screen.height;

        // ── 计算布局 ──
        calculateLayout(w, h);

        // ── 1. 绘制面板框架 ──
        drawPanelFrame(screen);

        // ── 2. 绘制标签栏 ──
        renderTabs(screen, mouseX, mouseY);

        // ── 3. 排序按钮列 ──
        if (activeTab == Tab.STORAGE) {
            sortButtons.render(screen, contentX, contentY + 2, mouseX, mouseY);
        }

        // ── 4. 合成底座（排序列下方） ──
        if (activeTab == Tab.STORAGE) {
            renderCraftDock(screen, mouseX, mouseY);
        }

        // ── 5. 分类面板 ──
        if (activeTab == Tab.STORAGE && categoryPanel != null && categoryPanel.isVisible()) {
            categoryPanel.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 6. 搜索栏 + 分页器 ──
        if (activeTab == Tab.STORAGE) {
            searchBar.render(screen, storageX, searchBarY, storageW, mouseX, mouseY);
        }

        // ── 7. 工具区 + 钉选槽位 ──
        if (activeTab == Tab.STORAGE) {
            renderToolArea(screen, mouseX, mouseY);
            renderPinSlots(screen, mouseX, mouseY);
        }

        // ── 8. 流体条 ──
        if (activeTab == Tab.STORAGE && fluidStrip != null && fluidStrip.isVisible()) {
            fluidStrip.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 9. 存储网格 ──
        if (activeTab == Tab.STORAGE && storageGrid != null && storageGrid.isVisible()) {
            storageGrid.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 10. 最近使用网格 ──
        if (activeTab == Tab.STORAGE && recentGrid != null && recentGrid.isVisible()) {
            recentGrid.render(screen, mouseX, mouseY, partialTicks);
        }

        // ── 11. 合成面板（可切换） ──
        if (activeTab == Tab.STORAGE && craftPanelVisible) {
            renderCraftablesPanel(screen, mouseX, mouseY);
        }

        // ── 蓝图标签页 ──
        if (activeTab == Tab.BLUEPRINTS) {
            renderBlueprintsContent(screen, mouseX, mouseY);
        }
    }

    // ======== 布局计算 ========

    private void calculateLayout(int screenW, int screenH) {
        StorageViewModel svm = state.storage;

        // 面板整体
        panelX = 4;
        panelW = screenW - 8;
        panelH = svm.panelHeight;
        panelY = screenH - panelH - 4;

        // 内容区
        contentX = panelX + PANEL_PADDING;
        contentY = panelY + HEADER_H + 4;

        // 分类面板（Issue 6: 右移避免与+/-按钮重叠）
        categoryX = contentX + SortButtonsView.getColumnWidth() + 20;
        categoryY = contentY;
        categoryH = Math.max(24, panelY + panelH - PANEL_PADDING - categoryY);

        // 存储区起始（分类面板右侧）
        storageX = categoryX + CATEGORY_W + CATEGORY_GAP;
        storageY = contentY;
        storageW = panelW - PANEL_PADDING * 2 - (storageX - contentX) - CRAFT_PANEL_W - CRAFT_PANEL_GAP;

        // 搜索栏
        searchBarY = storageY;

        // 工具区（搜索栏下方）
        toolY = searchBarY + SearchBarView.getTotalHeight() + 2;

        // 流体条（存储区左侧）
        boolean hasFluids = "fluids".equals(svm.activeCategory) || hasFluidItems();
        fluidStripW = FLUID_COLS * SLOT;
        boolean showFluid = hasFluids && storageW >= fluidStripW + SLOT * 3;
        fluidStripX = storageX;

        // 存储网格（流体条右侧）
        storageGridX = showFluid ? storageX + fluidStripW + 4 : storageX;
        storageGridW = showFluid ? storageW - fluidStripW - 4 : storageW;

        // 网格区（工具区下方）
        gridY = toolY + TOOL_AREA_H + 4;
        gridH = Math.max(SLOT, panelY + panelH - PANEL_PADDING - gridY);

        // 最近使用网格（存储网格右侧，合成面板左侧）
        recentX = storageGridX + storageGridW + STORAGE_RECENT_GAP;
        recentY = gridY;
        recentW = Math.max(SLOT, storageX + storageW - recentX);

        // 合成面板（右侧固定宽度）
        craftPanelX = panelX + panelW - PANEL_PADDING - CRAFT_PANEL_W;
        craftPanelY = contentY;
        craftPanelH = Math.max(40, panelY + panelH - PANEL_PADDING - craftPanelY);
    }

    // ======== 框架绘制 ========

    private void drawPanelFrame(GuiScreen screen) {
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xD014151A);
        Gui.drawRect(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER_H, 0xCC1C242F);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + 1, 0xFF64788E);
        Gui.drawRect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF0D1015);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + panelH, 0xFF64788E);
        Gui.drawRect(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF0D1015);
    }

    // ======== 标签栏 ========

    private void renderTabs(GuiScreen screen, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;

        fr.drawString("RTS", panelX + 8, panelY + 5, 0xFFF2F6FB);

        String[][] tabs = { { "STORAGE", "screen.rtsbuilding.storage.tab" },
            { "BLUEPRINTS", "screen.rtsbuilding.blueprints.tab" } };

        int tabX = panelX + 38;
        hoveredTab = -1;

        for (int i = 0; i < tabs.length; i++) {
            int tabW = i == 0 ? 60 : 70;
            String label = tryTranslate(tabs[i][1], tabs[i][0]);
            boolean active = (i == 0 && activeTab == Tab.STORAGE) || (i == 1 && activeTab == Tab.BLUEPRINTS);
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW
                && mouseY >= panelY + 2
                && mouseY <= panelY + HEADER_H - 1;

            if (hover) hoveredTab = i;

            int bgColor = active ? 0xCC355B4C : hover ? 0xAA334052 : 0x8826303B;
            int borderColor = active ? 0xFF7CCB93 : 0xFF536679;

            Gui.drawRect(tabX, panelY + 2, tabX + tabW, panelY + HEADER_H - 1, bgColor);
            Gui.drawRect(tabX, panelY + 2, tabX + tabW, panelY + 3, borderColor);

            fr.drawString(
                fr.trimStringToWidth(label, tabW - 8),
                tabX + tabW / 2 - fr.getStringWidth(label) / 2,
                panelY + 4,
                active ? 0xFFFFFFFF : 0xFFD8E2EE);

            tabX += tabW + 4;
        }

        drawSelectedPlacementStatus(fr, tabX);
    }

    private void drawSelectedPlacementStatus(FontRenderer fr, int startX) {
        int rightLimit = panelX + panelW - 16;
        if (startX >= rightLimit - 20) return;

        int maxW = Math.min(160, rightLimit - startX - 8);

        String statusText;
        int statusColor;
        if (state.interaction.selectedBlockId == null || state.interaction.selectedBlockId.isEmpty()
            || "minecraft:air".equals(state.interaction.selectedBlockId)) {
            statusText = StatCollector.translateToLocal("screen.rtsbuilding.status.selected_empty_hand");
            if (statusText == null || statusText.equals("screen.rtsbuilding.status.selected_empty_hand"))
                statusText = "Empty Hand";
            statusColor = 0xFF9B604B;
        } else {
            String itemName = state.interaction.selectedBlockId;
            int lastSlash = itemName.lastIndexOf(':');
            if (lastSlash >= 0 && lastSlash < itemName.length() - 1) {
                itemName = itemName.substring(lastSlash + 1);
            }
            statusText = StatCollector.translateToLocalFormatted("screen.rtsbuilding.status.selected_item", itemName);
            if (statusText == null || statusText.startsWith("screen.rtsbuilding")) statusText = "Selected: " + itemName;
            statusColor = 0xFFFCCB8A;
        }

        String trimmed = fr.trimStringToWidth(statusText, maxW);
        fr.drawString(trimmed, startX + 8, panelY + 5, statusColor);
    }

    // ======== 合成底座 ========

    private void renderCraftDock(GuiScreen screen, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;

        // 位置：排序列下方
        int dockX = contentX;
        int dockY = contentY + 2 + SortButtonsView.getColumnWidth() + 4;
        if (dockY + CRAFT_DOCK_C_SIZE > panelY + panelH - 2) return;

        // C 按钮
        int cX = dockX + 14;
        int cY = dockY + 10;
        int cBg = craftDockCHovered ? 0xCC385465 : 0xAA24303A;
        Gui.drawRect(cX, cY, cX + CRAFT_DOCK_C_SIZE, cY + CRAFT_DOCK_C_SIZE, cBg);
        Gui.drawRect(cX, cY, cX + CRAFT_DOCK_C_SIZE, cY + 1, 0xFF6E8799);
        Gui.drawRect(cX, cY + CRAFT_DOCK_C_SIZE - 1, cX + CRAFT_DOCK_C_SIZE, cY + CRAFT_DOCK_C_SIZE, 0xFF111821);
        fr.drawString("C", cX + CRAFT_DOCK_C_SIZE / 2 - fr.getStringWidth("C") / 2, cY + 5, 0xFFFFFFFF);

        craftDockCHovered = mouseX >= cX && mouseX <= cX + CRAFT_DOCK_C_SIZE
            && mouseY >= cY
            && mouseY <= cY + CRAFT_DOCK_C_SIZE;

        // 8 个绑定槽位围绕 C 按钮
        hoveredCraftDockSlot = -1;
        int ss = CRAFT_DOCK_SLOT_SIZE;
        int sg = CRAFT_DOCK_GAP;
        int cs = CRAFT_DOCK_C_SIZE;
        int[][] offsets = { { -ss - sg, -ss - sg }, { 0, -ss - sg }, { cs + sg, -ss - sg }, { -ss - sg, 0 },
            { cs + sg, 0 }, { -ss - sg, cs + sg }, { 0, cs + sg }, { cs + sg, cs + sg } };

        InteractionViewModel ivm = state.interaction;
        for (int i = 0; i < CRAFT_DOCK_SLOT_COUNT; i++) {
            int sx = cX + offsets[i][0];
            int sy = cY + offsets[i][1];
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
            if (mouseX >= sx && mouseX <= sx + ss && mouseY >= sy && mouseY <= sy + ss) {
                bg = isCapturing ? 0xDD377F53 : (isBound ? 0xBB2C4760 : 0xBB29323D);
                hoveredCraftDockSlot = i;
            }
            Gui.drawRect(sx, sy, sx + ss, sy + ss, bg);
            Gui.drawRect(sx, sy, sx + ss, sy + 1, 0xFF698097);
            Gui.drawRect(sx, sy + ss - 1, sx + ss, sy + ss, 0xFF0F151C);

            if (isBound) {
                String itemIdStr = binding.boundItemId;
                if (itemIdStr == null) {
                    itemIdStr = "";
                }
                ItemStack boundStack = state.storage.resolveStack(itemIdStr, binding.boundItemMeta);
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
                fr.drawString("+" + String.valueOf(i), sx + 1, sy + 1, 0xFFB0B0B0);
            }
        }
    }

    // ======== 工具区 ========

    private void renderToolArea(GuiScreen screen, int mouseX, int mouseY) {
        Minecraft mc = screen.mc;
        FontRenderer fr = mc.fontRenderer;
        if (mc.thePlayer == null) return;

        int toolX = storageGridX;
        hoveredToolSlot = -1;

        for (int i = 0; i < 9; i++) {
            int cx = toolX + i * HOTBAR_PITCH;
            int cy = toolY;

            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            boolean hasItem = stack != null && stack.getItem() != null;

            int bgColor = hasItem ? 0xAA1B1E25 : 0x881B1E25;
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bgColor);
            Gui.drawRect(cx, cy, cx + HOTBAR_SLOT, cy + 1, 0xFF5E6874);
            Gui.drawRect(cx, cy + HOTBAR_SLOT - 1, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            Gui.drawRect(cx, cy, cx + 1, cy + HOTBAR_SLOT, 0xFF5E6874);
            Gui.drawRect(cx + HOTBAR_SLOT - 1, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (hasItem) {
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_BLEND);
                // Issue 5: 物品图标缩小到0.75倍
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

            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                Gui.drawRect(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                hoveredToolSlot = i;
            }
        }

        // 空手按钮
        int emptyX = toolX + 9 * HOTBAR_PITCH;
        int emptyY = toolY;
        boolean emptyHandSelected = state.interaction.selectedBlockId == null
            || state.interaction.selectedBlockId.isEmpty()
            || "minecraft:air".equals(state.interaction.selectedBlockId);

        int emptyBg = emptyHandSelected ? 0xCC9B604B : 0xB06F5146;
        Gui.drawRect(emptyX, emptyY, emptyX + HOTBAR_SLOT, emptyY + HOTBAR_SLOT, emptyBg);
        Gui.drawRect(emptyX, emptyY, emptyX + HOTBAR_SLOT, emptyY + 1, 0xFFFFD0B0);
        Gui.drawRect(emptyX, emptyY + HOTBAR_SLOT - 1, emptyX + HOTBAR_SLOT, emptyY + HOTBAR_SLOT, 0xFF0C0D10);
        fr.drawString("\u270B", emptyX + 2, emptyY + 3, 0xFFFFFFFF);

        if (mouseX >= emptyX && mouseX <= emptyX + HOTBAR_SLOT && mouseY >= emptyY && mouseY <= emptyY + HOTBAR_SLOT) {
            hoveredToolSlot = 9;
        }
    }

    // ======== 钉选槽位 ========

    private void renderPinSlots(GuiScreen screen, int mouseX, int mouseY) {
        int pinX = toolX() + 10 * HOTBAR_PITCH + 12;
        int pinY = toolY;
        pinSlots.renderAt(screen, pinX, pinY, mouseX, mouseY);
    }

    private int toolX() {
        return storageGridX;
    }

    // ======== 合成面板 ========

    private void renderCraftablesPanel(GuiScreen screen, int mouseX, int mouseY) {
        if (state.craft.craftableEntries == null || state.craft.craftableEntries.isEmpty()) return;

        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;

        // 面板背景
        Gui.drawRect(craftPanelX, craftPanelY, craftPanelX + CRAFT_PANEL_W, craftPanelY + craftPanelH, 0xCC141922);

        // 标题栏
        Gui.drawRect(craftPanelX, craftPanelY, craftPanelX + CRAFT_PANEL_W, craftPanelY + HEADER_H, 0xAA1A1E2B);
        fr.drawString("Craft", craftPanelX + 5, craftPanelY + 4, 0xFFEAF2FF);

        // 搜索框 + OK/ALL 按钮
        int searchY = craftPanelY + HEADER_H + 1;
        int searchH = 12;
        int btnW = 18;
        int toggleW = 38;
        int gap = 2;

        int searchW = Math.max(30, CRAFT_PANEL_W - btnW - toggleW - gap * 3 - 4);
        int searchX = craftPanelX + 4;
        Gui.drawRect(searchX, searchY, searchX + searchW, searchY + searchH, 0xAA1E2731);
        Gui.drawRect(searchX, searchY, searchX + searchW, searchY + 1, 0xFF5E738A);

        String searchDisplay = state.craft.craftSearchQuery.isEmpty() ? "..." : state.craft.craftSearchQuery;
        fr.drawString(fr.trimStringToWidth(searchDisplay, searchW - 4), searchX + 2, searchY + 2, 0xFFCCCCCC);

        int applyX = searchX + searchW + gap;
        Gui.drawRect(applyX, searchY, applyX + btnW, searchY + searchH, 0xAA24303A);
        Gui.drawRect(applyX, searchY, applyX + btnW, searchY + 1, 0xFF6E8799);
        fr.drawString("OK", applyX + 4, searchY + 2, 0xFFFFFFFF);

        int toggleX = applyX + btnW + gap;
        int toggleBg = state.craft.craftShowAll ? 0xAA5A3D2A : 0xAA2C5A41;
        Gui.drawRect(toggleX, searchY, toggleX + toggleW, searchY + searchH, toggleBg);
        Gui.drawRect(toggleX, searchY, toggleX + toggleW, searchY + 1, 0xFF667D95);
        String toggleLabel = state.craft.craftShowAll ? "ALL" : "MAKE";
        fr.drawString(toggleLabel, toggleX + 4, searchY + 2, 0xFFFFFFFF);

        // 网格
        int cols = 4;
        int pitch = 20;
        int slotSize = 18;
        int gridStartY = searchY + searchH + 4;
        int gridAreaH = craftPanelH - (gridStartY - craftPanelY) - 4;
        int rows = Math.max(1, gridAreaH / pitch);

        List<CraftableEntry> entries = filterCraftEntries(state.craft.craftableEntries);
        int totalRows = (entries.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - rows);
        if (state.craft.craftScroll > maxScroll) state.craft.craftScroll = maxScroll;
        int startIdx = state.craft.craftScroll * cols;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = startIdx + row * cols + col;
                int sx = craftPanelX + 4 + col * pitch;
                int sy = gridStartY + row * pitch;

                int bg = 0xAA1A212B;
                if (idx < entries.size()) {
                    CraftableEntry entry = entries.get(idx);
                    boolean craftable = isEntryCraftable(entry);
                    bg = craftable ? 0xAA214131 : 0xAA3F2323;
                }
                Gui.drawRect(sx, sy, sx + slotSize, sy + slotSize, bg);
                Gui.drawRect(sx, sy, sx + slotSize, sy + 1, 0xFF596D84);
                Gui.drawRect(sx, sy + slotSize - 1, sx + slotSize, sy + slotSize, 0xFF11171E);

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
                                sx + slotSize - fr.getStringWidth(countStr) - 1,
                                sy + slotSize - 9,
                                0xFFFFFFFF);
                        }
                    }

                    if (mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize) {
                        Gui.drawRect(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x22FFFFFF);
                    }
                }
            }
        }
    }

    private void renderBlueprintsContent(GuiScreen screen, int mouseX, int mouseY) {
        FontRenderer fr = screen.mc.fontRenderer;
        String text = StatCollector.translateToLocal("screen.rtsbuilding.blueprints.placeholder");
        if (text == null || text.equals("screen.rtsbuilding.blueprints.placeholder")) text = "Blueprints (coming soon)";
        fr.drawString(text, contentX + 4, contentY + 4, 0xFFAAAAAA);
    }

    // ======== 交互 ========

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (activeTab == Tab.STORAGE) {
            // 搜索栏
            if (searchBar.onMouseClick(mouseX, mouseY, button)) return true;

            // 排序按钮
            if (sortButtons.onMouseClick(mouseX, mouseY, button)) return true;

            // 钉选槽位
            if (pinSlots.onMouseClick(mouseX, mouseY, button)) return true;

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
        }

        // 标签栏
        if (hoveredTab >= 0) {
            activeTab = (hoveredTab == 0) ? Tab.STORAGE : Tab.BLUEPRINTS;
            GuiScreen scr = Minecraft.getMinecraft().currentScreen;
            if (scr instanceof RtsScreen) {
                IRtsPanel bpPanel = ((RtsScreen) scr).getPanel("blueprint_panel");
                if (bpPanel instanceof BlueprintPanel) {
                    BlueprintPanel bp = (BlueprintPanel) bpPanel;
                    if (activeTab == Tab.BLUEPRINTS && !bp.isVisible()) bp.toggleVisibility();
                    else if (activeTab == Tab.STORAGE && bp.isVisible()) bp.toggleVisibility();
                }
            }
            return true;
        }

        // 合成底座 C 按钮
        if (craftDockCHovered && button == 0) {
            RtsNetworkManager.NETWORK
                .sendToServer(new com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalMessage());
            return true;
        }
        if (hoveredCraftDockSlot >= 0) {
            if (button == 0) {
                // 左键：进入 GUI 绑定模式
                InteractionViewModel ivm = state.interaction;
                ivm.guiBindingCaptureActive = true;
                ivm.guiBindingCaptureSlot = hoveredCraftDockSlot;
            } else if (button == 1) {
                // 右键：如果已绑定则直接打开 GUI
                RtsNetworkManager.NETWORK.sendToServer(
                    new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingMessage(
                        (byte) hoveredCraftDockSlot));
            }
            return true;
        }

        // 合成面板
        if (craftPanelVisible && handleCraftPanelClick(mouseX, mouseY)) return true;

        // 工具区
        if (hoveredToolSlot >= 0) {
            if (button == 0 || button == 1) {
                if (hoveredToolSlot < 9) {
                    ItemStack stack = Minecraft.getMinecraft().thePlayer.inventory.mainInventory[hoveredToolSlot];
                    if (stack != null && stack.getItem() != null) {
                        String itemId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                        state.interaction.selectedBlockId = itemId;
                        state.interaction.selectedBlockMeta = stack.getItemDamage();
                        state.interaction.selectedToolSlot = hoveredToolSlot;
                    } else {
                        state.interaction.selectedBlockId = "";
                        state.interaction.selectedBlockMeta = 0;
                        state.interaction.selectedToolSlot = hoveredToolSlot;
                    }
                } else {
                    state.interaction.selectedBlockId = "";
                    state.interaction.selectedBlockMeta = 0;
                    state.interaction.selectedToolSlot = -1;
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (activeTab == Tab.STORAGE) {
            // 搜索栏滚轮 → 翻页
            if (mouseY >= searchBarY && mouseY <= searchBarY + SearchBarView.SEARCH_BAR_H
                && mouseX >= storageX
                && mouseX <= storageX + storageW) {
                StorageViewModel svm = state.storage;
                if (!(svm.searchActive && !svm.searchQuery.isEmpty())) {
                    if (scroll > 0 && svm.currentPage > 0) {
                        svm.currentPage--;
                        requestStoragePage();
                        return true;
                    } else if (scroll < 0 && svm.currentPage < svm.totalPages - 1) {
                        svm.currentPage++;
                        requestStoragePage();
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

            // 合成面板滚轮
            if (craftPanelVisible && state.craft.craftableEntries != null && !state.craft.craftableEntries.isEmpty()) {
                if (mouseX >= craftPanelX && mouseX <= craftPanelX + CRAFT_PANEL_W
                    && mouseY >= craftPanelY
                    && mouseY <= craftPanelY + craftPanelH) {
                    if (scroll > 0 && state.craft.craftScroll > 0) {
                        state.craft.craftScroll--;
                        return true;
                    } else if (scroll < 0) {
                        state.craft.craftScroll++;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        if (activeTab == Tab.STORAGE) {
            if (searchBar.onKeyTyped(c, keyCode)) return true;
            if (storageGrid != null) return storageGrid.onKeyTyped(c, keyCode);
        }
        return false;
    }

    @Override
    public void resetFrameState() {
        hoveredTab = -1;
        hoveredToolSlot = -1;
        craftDockCHovered = false;
        hoveredCraftDockSlot = -1;
        sortButtons.resetFrameState();
        searchBar.resetFrameState();
        pinSlots.resetFrameState();
    }

    // ======== 合成面板点击 ========

    private boolean handleCraftPanelClick(int mouseX, int mouseY) {
        if (activeTab != Tab.STORAGE || state.craft.craftableEntries == null || state.craft.craftableEntries.isEmpty())
            return false;

        if (mouseX < craftPanelX || mouseX > craftPanelX + CRAFT_PANEL_W
            || mouseY < craftPanelY
            || mouseY > craftPanelY + craftPanelH) return false;

        int searchY = craftPanelY + HEADER_H + 1;
        int searchH = 12;
        int btnW = 18;
        int toggleW = 38;
        int gap = 2;
        int searchW = Math.max(30, CRAFT_PANEL_W - btnW - toggleW - gap * 3 - 4);

        int toggleX = (craftPanelX + 4) + searchW + gap + btnW + gap;
        if (mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= searchY && mouseY <= searchY + searchH) {
            state.craft.craftShowAll = !state.craft.craftShowAll;
            state.craft.craftScroll = 0;
            return true;
        }

        int gridStartY = searchY + searchH + 4;
        if (mouseY >= gridStartY) {
            int cols = 4;
            int pitch = 20;
            int col = (mouseX - craftPanelX - 4) / pitch;
            int row = (mouseY - gridStartY) / pitch;
            if (col >= 0 && col < cols && row >= 0) {
                List<CraftableEntry> entries = filterCraftEntries(state.craft.craftableEntries);
                int idx = state.craft.craftScroll * cols + row * cols + col;
                if (idx < entries.size()) {
                    CraftableEntry entry = entries.get(idx);
                    state.interaction.selectedBlockId = entry.itemId;
                    state.interaction.selectedBlockMeta = entry.meta;
                    state.interaction.addRecentBlock(entry.itemId);
                }
                return true;
            }
        }

        return true;
    }

    // ======== Getter 方法（供子面板使用） ========

    public int getPanelX() {
        return panelX;
    }

    public int getPanelY() {
        return panelY;
    }

    public int getPanelW() {
        return panelW;
    }

    public int getPanelH() {
        return panelH;
    }

    /** 分类面板 X */
    public int getCategoryX() {
        return categoryX;
    }

    /** 分类面板 Y */
    public int getCategoryY() {
        return categoryY;
    }

    /** 存储网格 X（已考虑流体条偏移） */
    public int getStorageX() {
        return storageGridX;
    }

    /** 存储网格 Y（工具区下方） */
    public int getStorageY() {
        return gridY;
    }

    /** 存储网格宽度 */
    public int getStorageW() {
        return storageGridW;
    }

    /** 网格区高度 */
    public int getGridH() {
        return gridH;
    }

    /** 流体条 X */
    public int getFluidStripX() {
        return fluidStripX;
    }

    /** 流体条宽度 */
    public int getFluidStripW() {
        return fluidStripW;
    }

    /** 最近使用网格 X */
    public int getRecentX() {
        return recentX;
    }

    /** 最近使用网格 Y */
    public int getRecentY() {
        return recentY;
    }

    /** 最近使用网格宽度 */
    public int getRecentW() {
        return recentW;
    }

    /** 合成面板 X */
    public int getCraftX() {
        return craftPanelX;
    }

    /** 合成面板 Y */
    public int getCraftY() {
        return craftPanelY;
    }

    /** 合成面板宽度 */
    public int getCraftW() {
        return CRAFT_PANEL_W;
    }

    /** 合成面板高度 */
    public int getCraftH() {
        return craftPanelH;
    }

    /** 当前是否存储标签页 */
    public boolean isStorageTab() {
        return activeTab == Tab.STORAGE;
    }

    /** 搜索栏是否聚焦 */
    public boolean isSearchFocused() {
        return searchBar.isFocused();
    }

    // ======== 内部辅助 ========

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

    private void requestStoragePage() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof RtsScreen) {
            ((RtsScreen) mc.currentScreen).requestStoragePage(state.storage.currentPage);
        }
    }

    private List<CraftableEntry> filterCraftEntries(List<CraftableEntry> entries) {
        if (entries == null || entries.isEmpty()) return entries;
        String q = state.craft.craftSearchQuery.trim()
            .toLowerCase();
        if (q.isEmpty()) {
            if (!state.craft.craftShowAll) {
                java.util.ArrayList<CraftableEntry> filtered = new java.util.ArrayList<>();
                for (CraftableEntry e : entries) {
                    if (isEntryCraftable(e)) filtered.add(e);
                }
                return filtered;
            }
            return entries;
        }
        java.util.ArrayList<CraftableEntry> filtered = new java.util.ArrayList<>();
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
