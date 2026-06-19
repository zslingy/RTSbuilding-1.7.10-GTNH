package com.rtsbuilding.rtsbuilding.client.panel.storage;

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

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

import cpw.mods.fml.common.registry.GameData;

/**
 * 存储物品网格 — 固定列数自适应行数网格，渲染存储中的物品。
 * 搜索栏已移至 SearchBarView，本类仅负责网格渲染和点击选中。
 */
public class StorageGridView implements IRtsPanel {

    private static final String PANEL_NAME = "storage_grid";
    private static final int COLS = 20;
    private static final int ROWS_COUNT = 2;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 0;

    /** 1.7.10 遗留物品名称映射（客户端也需要，用于 resolveStack） */
    private static final java.util.Map<String, String[]> LEGACY_NAMES = new java.util.HashMap<>();
    static {
        LEGACY_NAMES.put("lapis_lazuli", new String[] { "dye", "4" });
        LEGACY_NAMES.put("oak_log", new String[] { "log", "0" });
        LEGACY_NAMES.put("oak_planks", new String[] { "planks", "0" });
        LEGACY_NAMES.put("stone_bricks", new String[] { "stonebrick", "0" });
        LEGACY_NAMES.put("bricks", new String[] { "brick_block", "0" });
    }

    private int gridX, gridY, gridW, gridH;
    private int hoveredSlot = -1;
    private int selectedSlot = -1;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();

    public StorageGridView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(gridX, gridY, gridW, gridH);
    }

    @Override
    public boolean isVisible() {
        RtsBottomPanel bp = findBottomPanel();
        return bp == null || bp.isStorageTab();
    }

    private static RtsBottomPanel findBottomPanel() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.currentScreen instanceof RtsScreen) {
            IRtsPanel panel = ((RtsScreen) mc.currentScreen).getPanel(RtsBottomPanel.PANEL_NAME);
            if (panel instanceof RtsBottomPanel) return (RtsBottomPanel) panel;
        }
        return null;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        StorageViewModel svm = state.storage;
        FontRenderer fr = screen.mc.fontRenderer;

        // 从 RtsBottomPanel 获取坐标（已考虑流体条偏移和搜索栏/工具区高度）
        RtsBottomPanel bp = (RtsBottomPanel) ((RtsScreen) screen).getPanel("bottom_panel");
        gridX = bp.getStorageX();
        gridY = bp.getStorageY();
        gridW = COLS * (SLOT_SIZE + SLOT_SPACING);
        gridH = Math.min(ROWS() * (SLOT_SIZE + SLOT_SPACING), bp.getGridH());

        // hover 检测
        hoveredSlot = -1;
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int col = (mouseX - gridX) / (SLOT_SIZE + SLOT_SPACING);
            int row = (mouseY - gridY) / (SLOT_SIZE + SLOT_SPACING);
            if (col >= 0 && col < COLS && row >= 0 && row < ROWS()) {
                hoveredSlot = row * COLS + col;
            }
        }

        // 绘制槽位和物品
        List<StorageEntry> displayEntries = svm.getDisplayEntries();
        boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();
        int pageStart = isSearchMode ? 0 : svm.currentPage * svm.entriesPerPage;
        int maxItems = Math.min(COLS * ROWS(), displayEntries.size() - pageStart);
        ItemStack hoveredStack = null;

        // [调试日志] 问题11: 确认存储网格渲染状态
        com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.debug(
            "StorageGridView: render entries={}, displayEntries={}, pageStart={}, maxItems={}, gridX={}, gridY={}, gridW={}, gridH={}",
            svm.entries.size(),
            displayEntries.size(),
            pageStart,
            maxItems,
            gridX,
            gridY,
            gridW,
            gridH);

        int rows = ROWS();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLS; col++) {
                int slotIndex = row * COLS + col;
                int sx = gridX + col * (SLOT_SIZE + SLOT_SPACING);
                int sy = gridY + row * (SLOT_SIZE + SLOT_SPACING);

                int bgColor = 0x88444444;
                if (slotIndex == hoveredSlot) bgColor = 0x88666666;
                if (slotIndex == selectedSlot) bgColor = 0x886688CC;
                Gui.drawRect(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, bgColor);

                int entryIndex = pageStart + slotIndex;
                if (entryIndex < displayEntries.size()) {
                    StorageEntry entry = displayEntries.get(entryIndex);
                    if (entry != null) {
                        // 直接使用 entry.stack 渲染，像快捷栏一样无需注册表查找
                        ItemStack stack = entry.stack;
                        if (slotIndex == hoveredSlot) hoveredStack = stack;
                        renderEntry(screen, stack, sx, sy);
                    }
                }
            }
        }

        // 工具提示
        if (hoveredStack != null) {
            List<String> lines = hoveredStack
                .getTooltip(screen.mc.thePlayer, screen.mc.gameSettings.advancedItemTooltips);
            renderTooltipLines(screen, lines, mouseX, mouseY);
        }
    }

    private int ROWS() {
        return ROWS_COUNT;
    }

    private void renderEntry(GuiScreen screen, ItemStack stack, int sx, int sy) {
        if (stack == null) return;
        Minecraft mc = screen.mc;
        FontRenderer fr = mc.fontRenderer;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Issue 5: 物品图标缩小到0.75倍
        float iconScale = 0.75f;
        float offset = (SLOT_SIZE - SLOT_SIZE * iconScale) / 2.0f;
        GL11.glTranslatef(sx + offset, sy + offset, 0);
        GL11.glScalef(iconScale, iconScale, 1.0f);
        RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, stack, 1, 1);
        renderItem.renderItemOverlayIntoGUI(fr, mc.renderEngine, stack, 1, 1);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private ItemStack resolveStack(StorageEntry entry) {
        if (entry == null || entry.itemId == null) return null;
        try {
            // 尝试1: 直接用 ResourceLocation 从物品注册表查找
            ResourceLocation rl = new ResourceLocation(entry.itemId);
            net.minecraft.item.Item item = (net.minecraft.item.Item) GameData.getItemRegistry()
                .getObject(rl);
            if (item != null) return new ItemStack(item, 1, entry.meta);

            // 尝试2: 从方块注册表查找
            net.minecraft.block.Block block = (net.minecraft.block.Block) GameData.getBlockRegistry()
                .getObject(rl);
            if (block != null) {
                net.minecraft.item.Item blockItem = net.minecraft.item.Item.getItemFromBlock(block);
                if (blockItem != null) return new ItemStack(blockItem, 1, entry.meta);
            }

            // 尝试3: 去掉 "minecraft:" 前缀后查找
            String lookupId = entry.itemId;
            if (lookupId.startsWith("minecraft:")) {
                lookupId = lookupId.substring("minecraft:".length());
            }
            ResourceLocation rl2 = new ResourceLocation(lookupId);
            item = (net.minecraft.item.Item) GameData.getItemRegistry()
                .getObject(rl2);
            if (item != null) return new ItemStack(item, 1, entry.meta);

            block = (net.minecraft.block.Block) GameData.getBlockRegistry()
                .getObject(rl2);
            if (block != null) {
                net.minecraft.item.Item blockItem = net.minecraft.item.Item.getItemFromBlock(block);
                if (blockItem != null) return new ItemStack(blockItem, 1, entry.meta);
            }

            // 尝试4: 遗留名称映射
            String[] legacy = LEGACY_NAMES.get(lookupId);
            if (legacy != null) {
                String legacyId = legacy[0];
                int legacyMeta = legacy.length > 1 ? Integer.parseInt(legacy[1]) : entry.meta;
                ResourceLocation rl3 = new ResourceLocation(legacyId);
                item = (net.minecraft.item.Item) GameData.getItemRegistry()
                    .getObject(rl3);
                if (item != null) return new ItemStack(item, 1, legacyMeta);
                block = (net.minecraft.block.Block) GameData.getBlockRegistry()
                    .getObject(rl3);
                if (block != null) {
                    net.minecraft.item.Item blockItem = net.minecraft.item.Item.getItemFromBlock(block);
                    if (blockItem != null) return new ItemStack(blockItem, 1, legacyMeta);
                }
            }

            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER
                .debug("StorageGridView.resolveStack: FAILED itemId={} meta={}", entry.itemId, entry.meta);
        } catch (Exception e) {
            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.debug(
                "StorageGridView.resolveStack: EXCEPTION itemId={} meta={} error={}",
                entry.itemId,
                entry.meta,
                e.getMessage());
        }
        return null;
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) return false;

        int col = (mouseX - gridX) / (SLOT_SIZE + SLOT_SPACING);
        int row = (mouseY - gridY) / (SLOT_SIZE + SLOT_SPACING);

        if (col >= 0 && col < COLS && row >= 0 && row < ROWS()) {
            int slotIndex = row * COLS + col;
            selectedSlot = slotIndex;

            StorageViewModel svm = state.storage;
            List<StorageEntry> displayEntries = svm.getDisplayEntries();
            boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();
            int pageStart = isSearchMode ? 0 : svm.currentPage * svm.entriesPerPage;
            int entryIndex = pageStart + slotIndex;

            if (entryIndex < displayEntries.size()) {
                StorageEntry entry = displayEntries.get(entryIndex);
                if (entry != null) {
                    state.interaction.selectedBlockId = entry.itemId;
                    state.interaction.selectedBlockMeta = entry.meta;
                    state.interaction.addRecentBlock(entry.itemId);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= gridY && mouseY <= gridY + gridH) {
            StorageViewModel svm = state.storage;
            if (svm.searchActive && !svm.searchQuery.isEmpty()) return false;
            if (scroll > 0 && svm.currentPage > 0) {
                svm.currentPage--;
                return true;
            } else if (scroll < 0 && svm.currentPage < svm.totalPages - 1) {
                svm.currentPage++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        // 搜索输入已移至 SearchBarView
        return false;
    }

    @Override
    public void resetFrameState() {
        hoveredSlot = -1;
    }

    private static void renderTooltipLines(GuiScreen screen, List<String> lines, int x, int y) {
        if (lines == null || lines.isEmpty()) return;
        FontRenderer fr = screen.mc.fontRenderer;

        int maxWidth = 0;
        for (String line : lines) {
            int w = fr.getStringWidth(line);
            if (w > maxWidth) maxWidth = w;
        }

        int drawX = x + 12;
        int drawY = y - 12;
        int lineHeight = 10;
        int height = lines.size() * lineHeight;
        if (drawY < 4) drawY = 4;

        Gui.drawRect(drawX - 3, drawY - 4, drawX + maxWidth + 3, drawY + height + 3, 0xC0100010);
        for (int i = 0; i < lines.size(); i++) {
            fr.drawStringWithShadow(lines.get(i), drawX, drawY + i * lineHeight, 0xFFFFFFFF);
        }
    }
}
