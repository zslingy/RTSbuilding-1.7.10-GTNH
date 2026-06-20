package com.rtsbuilding.rtsbuilding.client.panel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;

/**
 * 搜索栏 + 分页器 — 位于存储网格上方。
 *
 * 重构版本：使用 Minecraft 原生 {@link GuiTextField} 替代自定义文本渲染，
 * 获得完整的文本选择、复制粘贴、光标导航能力。
 *
 * 布局（水平排列）：
 * [搜索输入框 (GuiTextField)] [x 清除] [< 页码 >]
 */
public class SearchBarView {

    public static final int SEARCH_FIELD_H = 14;
    public static final int SEARCH_BAR_GAP = 2;
    private static final int CLEAR_BTN_SIZE = 12;
    private static final int PAGER_W = 78;

    private final RtsClientState state;
    private final GuiTextField textField;

    private int barX, barY, barW;
    private int clearX, clearY;
    private boolean clearHovered = false;
    private int prevPageX, nextPageX;
    private boolean prevHovered = false;
    private boolean nextHovered = false;

    public SearchBarView() {
        this.state = RtsClientState.get();
        // 临时初始化，坐标在 render 中更新
        this.textField = new GuiTextField(MinecraftTemp.getFontRenderer(), 0, 0, 100, SEARCH_FIELD_H);
        this.textField.setMaxStringLength(64);
        this.textField.setEnableBackgroundDrawing(false);
        syncFromStorage();
    }

    /** 搜索栏总高度（含间距） */
    public static int getTotalHeight() {
        return 17;
    }

    /**
     * 渲染搜索栏。
     * 
     * @param baseX 存储区起始 X
     * @param baseY 存储区起始 Y
     * @param width 存储区可用宽度
     */
    public void render(GuiScreen screen, int baseX, int baseY, int width, int mouseX, int mouseY) {
        this.barX = baseX;
        this.barY = baseY;
        this.barW = width;

        FontRenderer fr = screen.mc.fontRenderer;
        StorageViewModel svm = state.storage;

        // ── 搜索输入框区域 ──
        int inputW = Math.max(56, barW - CLEAR_BTN_SIZE - 4 - 82);
        int fieldW = Math.max(56, inputW - 4);
        textField.xPosition = barX + 2;
        textField.yPosition = barY + 1;
        textField.width = fieldW;
        textField.height = SEARCH_FIELD_H - 2;

        // 同步 StorageViewModel 到文本框
        syncFromStorage();

        // 搜索框背景
        Gui.drawRect(barX, barY, barX + inputW, barY + SEARCH_FIELD_H, 0xCC0C0C0C);
        Gui.drawRect(barX, barY, barX + inputW, barY + 1, 0xFF5E738A);

        // 绘制 GuiTextField
        if (!textField.isFocused() && textField.getText()
            .isEmpty()) {
            String hint = tryTranslate("screen.rtsbuilding.search.click_to_search", "Search...");
            fr.drawString(hint, barX + 4, barY + 3, 0xFF666666);
        }
        textField.drawTextBox();

        // ── 清除按钮 ──
        clearX = barX + inputW + 2;
        clearY = barY + 1;
        clearHovered = inside(mouseX, mouseY, clearX, clearY, CLEAR_BTN_SIZE, CLEAR_BTN_SIZE);
        boolean hasText = !textField.getText()
            .isEmpty() || textField.isFocused();
        int clearBg = clearHovered ? 0xCC41576F : 0xAA2B3542;
        Gui.drawRect(clearX, clearY, clearX + CLEAR_BTN_SIZE, clearY + CLEAR_BTN_SIZE, clearBg);
        Gui.drawRect(clearX, clearY, clearX + CLEAR_BTN_SIZE, clearY + 1, 0xFF637283);
        Gui.drawRect(clearX, clearY + CLEAR_BTN_SIZE - 1, clearX + CLEAR_BTN_SIZE, clearY + CLEAR_BTN_SIZE, 0xFF101318);
        Gui.drawRect(clearX, clearY, clearX + 1, clearY + CLEAR_BTN_SIZE, 0xFF637283);
        Gui.drawRect(clearX + CLEAR_BTN_SIZE - 1, clearY, clearX + CLEAR_BTN_SIZE, clearY + CLEAR_BTN_SIZE, 0xFF101318);
        fr.drawString("x", clearX + 4, clearY + 2, hasText ? 0xFFFFFFFF : 0xFF99A6B5);

        // ── 分页器（在存储网格宽度右侧）──
        boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();
        int pagerX = clearX + CLEAR_BTN_SIZE + 4;
        if (!isSearchMode) {
            int page = svm.currentPage;
            int total = Math.max(1, svm.totalPages);
            String pageText = (page + 1) + "/" + total;

            prevPageX = pagerX;
            nextPageX = pagerX + 58;
            boolean canPrev = page > 0;
            boolean canNext = page < total - 1;
            prevHovered = canPrev && inside(mouseX, mouseY, prevPageX, clearY, 16, 14);
            nextHovered = canNext && inside(mouseX, mouseY, nextPageX, clearY, 16, 14);

            Gui.drawRect(prevPageX, clearY, prevPageX + 16, clearY + 14, prevHovered ? 0xCC41576F : 0xAA2A2A2A);
            fr.drawString(
                "<",
                prevPageX + 5,
                clearY + 3,
                canPrev ? (prevHovered ? 0xFFFFFF44 : 0xFFFFFFFF) : 0xFF444444);

            Gui.drawRect(nextPageX, clearY, nextPageX + 16, clearY + 14, nextHovered ? 0xCC41576F : 0xAA2A2A2A);
            fr.drawString(
                ">",
                nextPageX + 5,
                clearY + 3,
                canNext ? (nextHovered ? 0xFFFFFF44 : 0xFFFFFFFF) : 0xFF444444);

            fr.drawString(pageText, pagerX + 20, clearY + 3, 0xFFFFFFFF);
        }
    }

    /** 处理鼠标点击 */
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        StorageViewModel svm = state.storage;

        // GuiTextField 点击（焦点获取/失焦）
        textField.mouseClicked(mouseX, mouseY, button);

        // 清除按钮
        if (clearHovered) {
            textField.setText("");
            svm.clearSearch();
            return true;
        }

        // 翻页按钮
        boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();
        if (!isSearchMode) {
            if (prevHovered && svm.currentPage > 0) {
                svm.currentPage--;
                requestPage();
                return true;
            }
            if (nextHovered && svm.currentPage < svm.totalPages - 1) {
                svm.currentPage++;
                requestPage();
                return true;
            }
        }

        // 搜索框区域被点击
        int inputW = Math.max(56, barW - CLEAR_BTN_SIZE - 4 - 82);
        if (mouseX >= barX && mouseX <= barX + inputW && mouseY >= barY && mouseY <= barY + SEARCH_FIELD_H) {
            return true;
        }

        return false;
    }

    /** 处理键盘输入 */
    public boolean onKeyTyped(char c, int keyCode) {
        if (!textField.isFocused()) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            textField.setFocused(false);
            return true;
        }

        String before = textField.getText();
        textField.textboxKeyTyped(c, keyCode);
        String after = textField.getText();

        if (!after.equals(before)) {
            state.storage.applySearch(after);
        }
        return true;
    }

    /** 每 tick 更新光标闪烁 */
    public void updateCursorCounter() {
        textField.updateCursorCounter();
    }

    /** 是否获得焦点 */
    public boolean isFocused() {
        return textField.isFocused();
    }

    /** 强制失焦 */
    public void blur() {
        textField.setFocused(false);
    }

    public void resetFrameState() {
        clearHovered = false;
        prevHovered = false;
        nextHovered = false;
    }

    // ======== 内部 ========

    private void syncFromStorage() {
        StorageViewModel svm = state.storage;
        String expected = svm.searchQuery == null ? "" : svm.searchQuery;
        if (!textField.isFocused() && !expected.equals(textField.getText())) {
            textField.setText(expected);
        }
    }

    private void requestPage() {
        RtsNetworkManager.NETWORK
            .sendToServer(new C2SRtsRequestStoragePageMessage(state.storage.currentPage, 0, state.storage.sortMode));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String tryTranslate(String key, String fallback) {
        String s = StatCollector.translateToLocal(key);
        return (s == null || s.equals(key)) ? fallback : s;
    }

    /** 辅助获取 FontRenderer（避免在构造函数中引用 Minecraft） */
    private static final class MinecraftTemp {

        static FontRenderer getFontRenderer() {
            return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        }
    }
}
