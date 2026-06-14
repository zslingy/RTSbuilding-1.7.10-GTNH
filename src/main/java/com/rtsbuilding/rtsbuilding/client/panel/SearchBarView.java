package com.rtsbuilding.rtsbuilding.client.panel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;

/**
 * 搜索栏 + 分页器 — 位于存储网格上方。
 * 对齐原版 BottomPanel 的 EditBox + pager 区域。
 *
 * 布局（水平排列）：
 * [🔍 搜索输入框] [x 清除] [< 页码 >]
 *
 * 点击搜索框获得焦点，键盘输入搜索文本。
 * 搜索在客户端本地完成（RtsPinyinSearch），服务端不参与拼音匹配。
 */
public class SearchBarView {

    public static final int SEARCH_BAR_H = 14;
    public static final int SEARCH_BAR_GAP = 4;
    private static final int CLEAR_BTN_W = 14;
    private static final int PAGER_W = 60;

    private final RtsClientState state;

    private int barX, barY, barW;
    private boolean searchFocused = false;
    private int cursorTicks = 0;
    private boolean clearHovered = false;
    private boolean prevHovered = false;
    private boolean nextHovered = false;

    public SearchBarView() {
        this.state = RtsClientState.get();
    }

    /** 搜索栏总高度（含间距） */
    public static int getTotalHeight() {
        return SEARCH_BAR_H + SEARCH_BAR_GAP;
    }

    /**
     * 渲染搜索栏。
     * 
     * @param baseX 存储区起始X
     * @param baseY 存储区起始Y（contentY）
     * @param width 存储区可用宽度
     */
    public void render(GuiScreen screen, int baseX, int baseY, int width, int mouseX, int mouseY) {
        this.barX = baseX;
        this.barY = baseY;
        this.barW = width;

        FontRenderer fr = screen.mc.fontRenderer;
        StorageViewModel svm = state.storage;

        // ── 搜索输入框 ──
        int inputW = barW - CLEAR_BTN_W - 4 - PAGER_W;
        if (inputW < 30) inputW = 30;

        int bg = searchFocused ? 0xCC333333 : 0x88333333;
        Gui.drawRect(barX, barY, barX + inputW, barY + SEARCH_BAR_H, bg);
        Gui.drawRect(barX, barY, barX + inputW, barY + 1, 0xFF5E738A);

        // 搜索文本 + 光标
        cursorTicks++;
        String prefix = "\uD83D\uDD0D ";
        String displayText = prefix + svm.searchQuery;
        if (searchFocused && (cursorTicks / 20) % 2 == 0) {
            displayText = displayText + "|";
        }
        int maxChars = inputW / 6 - 2;
        if (displayText.length() > maxChars) {
            displayText = displayText.substring(displayText.length() - maxChars);
        }
        fr.drawString(displayText, barX + 2, barY + 3, 0xFFCCCCCC);

        // 未聚焦且无搜索词时的占位文本
        if (!searchFocused && svm.searchQuery.isEmpty()) {
            String hint = StatCollector.translateToLocal("screen.rtsbuilding.search.click_to_search");
            if (hint == null || hint.equals("screen.rtsbuilding.search.click_to_search")) hint = "Search...";
            fr.drawString(hint, barX + 2, barY + 3, 0xFF666666);
        }

        // ── 清除按钮 ──
        int clearX = barX + inputW + 2;
        clearHovered = mouseX >= clearX && mouseX <= clearX + CLEAR_BTN_W
            && mouseY >= barY
            && mouseY <= barY + SEARCH_BAR_H;
        Gui.drawRect(clearX, barY, clearX + CLEAR_BTN_W, barY + SEARCH_BAR_H, clearHovered ? 0xCC884444 : 0xAA443333);
        fr.drawString("x", clearX + CLEAR_BTN_W / 2 - fr.getStringWidth("x") / 2, barY + 3, 0xFFFF8888);

        // ── 分页器 ──
        int pagerX = clearX + CLEAR_BTN_W + 4;
        boolean isSearchMode = svm.searchActive && !svm.searchQuery.isEmpty();

        if (isSearchMode) {
            // 搜索模式：显示结果数量
            String resultInfo = StatCollector
                .translateToLocalFormatted("screen.rtsbuilding.search.found_items", svm.filteredEntries.size());
            if (resultInfo == null || resultInfo.startsWith("screen.rtsbuilding")) {
                resultInfo = "Found " + svm.filteredEntries.size() + " items";
            }
            fr.drawString(resultInfo, pagerX, barY + 3, 0xFF44CC44);
            prevHovered = false;
            nextHovered = false;
        } else {
            // 正常模式：翻页
            int pagerCX = pagerX + PAGER_W / 2;
            String pageInfo = StatCollector.translateToLocalFormatted(
                "screen.rtsbuilding.search.page_info",
                svm.currentPage + 1,
                Math.max(1, svm.totalPages));
            if (pageInfo == null || pageInfo.startsWith("screen.rtsbuilding")) {
                pageInfo = (svm.currentPage + 1) + " / " + Math.max(1, svm.totalPages);
            }
            fr.drawString(pageInfo, pagerCX - fr.getStringWidth(pageInfo) / 2, barY + 3, 0xFFAAAAAA);

            boolean canPrev = svm.currentPage > 0;
            boolean canNext = svm.currentPage < svm.totalPages - 1;

            prevHovered = canPrev && mouseX >= pagerX
                && mouseX <= pagerX + 14
                && mouseY >= barY
                && mouseY <= barY + SEARCH_BAR_H;
            nextHovered = canNext && mouseX >= pagerX + PAGER_W - 14
                && mouseX <= pagerX + PAGER_W
                && mouseY >= barY
                && mouseY <= barY + SEARCH_BAR_H;

            fr.drawString("\u25C0", pagerX, barY + 3, canPrev ? (prevHovered ? 0xFFFFFF44 : 0xFFCCCCCC) : 0xFF444444);
            fr.drawString(
                "\u25B6",
                pagerX + PAGER_W - 14,
                barY + 3,
                canNext ? (nextHovered ? 0xFFFFFF44 : 0xFFCCCCCC) : 0xFF444444);
        }
    }

    /** 处理鼠标点击，返回 true 表示消费 */
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        StorageViewModel svm = state.storage;

        // 搜索框点击 → 获得焦点
        int inputW = barW - CLEAR_BTN_W - 4 - PAGER_W;
        if (inputW < 30) inputW = 30;
        if (mouseX >= barX && mouseX <= barX + inputW && mouseY >= barY && mouseY <= barY + SEARCH_BAR_H) {
            searchFocused = true;
            return true;
        }

        // 点击外部 → 失去焦点
        searchFocused = false;

        // 清除按钮
        int clearX = barX + inputW + 2;
        if (clearHovered) {
            svm.clearSearch();
            return true;
        }

        // 翻页按钮
        int pagerX = clearX + CLEAR_BTN_W + 4;
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

        return false;
    }

    /** 处理键盘输入，返回 true 表示消费 */
    public boolean onKeyTyped(char c, int keyCode) {
        if (!searchFocused) return false;
        StorageViewModel svm = state.storage;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            svm.clearSearch();
            searchFocused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            searchFocused = false;
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!svm.searchQuery.isEmpty()) {
                svm.applySearch(svm.searchQuery.substring(0, svm.searchQuery.length() - 1));
            }
            return true;
        }
        if (c >= 32 && c < 127) {
            svm.applySearch(svm.searchQuery + c);
            return true;
        }
        return false;
    }

    public void resetFrameState() {
        clearHovered = false;
        prevHovered = false;
        nextHovered = false;
        cursorTicks++;
    }

    public boolean isFocused() {
        return searchFocused;
    }

    private void requestPage() {
        RtsNetworkManager.NETWORK
            .sendToServer(new C2SRtsRequestStoragePageMessage(state.storage.currentPage, 0, state.storage.sortMode));
    }
}
