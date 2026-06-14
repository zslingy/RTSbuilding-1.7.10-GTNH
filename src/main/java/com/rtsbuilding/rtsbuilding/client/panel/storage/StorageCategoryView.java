package com.rtsbuilding.rtsbuilding.client.panel.storage;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

/**
 * 存储分类侧栏 — 左列分类标签（ALL/BLOCKS/ITEMS/TOOLS/...）。
 * 点击切换 StorageViewModel.activeCategory。
 *
 * Bug4修复 (2026-06-11): 坐标从硬编码改为从 RtsBottomPanel 动态获取。
 */
public class StorageCategoryView implements IRtsPanel {

    private static final String PANEL_NAME = "storage_category";
    private static final int TAB_WIDTH = 124;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_SPACING = 2;
    private static final int ALWAYS_VISIBLE = 5;
    private static final float ANIM_SPEED = 0.15f;

    private static final String[][] CATEGORIES = { { "all", "ALL" }, { "blocks", "BLOCKS" }, { "items", "ITEMS" },
        { "tools", "TOOLS" }, { "weapons", "WEAPONS" }, { "armor", "ARMOR" }, { "food", "FOOD" },
        { "redstone", "REDSTONE" }, { "fluids", "FLUIDS" }, };

    private final RtsClientState state;
    private int catX, catY, catH;
    private boolean expanded = false;
    private float animProgress = 0.0f;
    private int visibleCount = ALWAYS_VISIBLE;
    private boolean toggleHovered = false;

    public StorageCategoryView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(catX, catY, TAB_WIDTH, catH);
    }

    @Override
    public boolean isVisible() {
        // Bug2修复：仅在存储标签页激活时显示
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
        FontRenderer fr = screen.mc.fontRenderer;

        RtsBottomPanel bp = findBottomPanel(screen);
        if (bp != null) {
            catX = bp.getCategoryX();
            catY = bp.getCategoryY();
        } else {
            catX = 12;
            catY = screen.height - 110 - 4 + 18 + 4 + 14 + 2;
        }

        // Bug7b修复：动画平滑过渡 visibleCount
        float target = expanded ? CATEGORIES.length : ALWAYS_VISIBLE + 1;
        animProgress += (target - animProgress) * ANIM_SPEED * (partialTicks + 1.0f);
        if (Math.abs(target - animProgress) < 0.05f) animProgress = target;
        int displayCount = Math.round(animProgress);
        catH = displayCount * (TAB_HEIGHT + TAB_SPACING);

        // 折叠/展开切换按钮（始终在顶部）
        int toggleY = catY;
        String toggleIcon = expanded ? "\u25B2" : "\u25BC";
        boolean hoverToggle = mouseX >= catX && mouseX <= catX + TAB_WIDTH
            && mouseY >= toggleY
            && mouseY <= toggleY + TAB_HEIGHT;
        toggleHovered = hoverToggle;
        Gui.drawRect(catX, toggleY, catX + TAB_WIDTH, toggleY + TAB_HEIGHT, hoverToggle ? 0xCC555555 : 0xBB333333);
        fr.drawString(toggleIcon + " Categories", catX + 4, toggleY + 4, 0xFFAAAAAA);

        int startY = toggleY + TAB_HEIGHT + TAB_SPACING;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (i >= displayCount - 1) break;
            String key = CATEGORIES[i][0];
            String label = CATEGORIES[i][1];
            int ty = startY + i * (TAB_HEIGHT + TAB_SPACING);
            boolean active = key.equals(state.storage.activeCategory);

            int bgColor = active ? 0xCC6688CC : 0xCC444444;
            Gui.drawRect(catX, ty, catX + TAB_WIDTH, ty + TAB_HEIGHT, bgColor);

            int textColor = active ? 0xFFFFFF44 : 0xFFCCCCCC;
            fr.drawString(label, catX + 4, ty + 4, textColor);
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        int toggleY = catY;
        if (mouseX >= catX && mouseX <= catX + TAB_WIDTH && mouseY >= toggleY && mouseY <= toggleY + TAB_HEIGHT) {
            expanded = !expanded;
            return true;
        }

        int startY = toggleY + TAB_HEIGHT + TAB_SPACING;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int ty = startY + i * (TAB_HEIGHT + TAB_SPACING);
            if (mouseX >= catX && mouseX <= catX + TAB_WIDTH && mouseY >= ty && mouseY <= ty + TAB_HEIGHT) {
                state.storage.activeCategory = CATEGORIES[i][0];
                state.storage.currentPage = 0;
                state.storage.dirty = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        return false;
    }

    @Override
    public void resetFrameState() {}

    /**
     * 查找父 RtsScreen 中的 RtsBottomPanel 面板。
     */
    private static RtsBottomPanel findBottomPanel(GuiScreen screen) {
        if (screen instanceof RtsScreen) {
            IRtsPanel panel = ((RtsScreen) screen).getPanel(RtsBottomPanel.PANEL_NAME);
            if (panel instanceof RtsBottomPanel) {
                return (RtsBottomPanel) panel;
            }
        }
        return null;
    }
}
