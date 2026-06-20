package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;

/**
 * 底部面板不可变布局参数。
 *
 * 由 {@link RtsBottomPanel#resolveLayout(int, int)} 每帧计算一次，
 * 所有子区域坐标从该结构获取，消除散落的可变字段。
 */
final class BottomPanelLayout {

    // ── 面板整体 ──
    final int panelX, panelY, panelW, panelH;
    // ── 内容区起点 ──
    final int contentX, contentY;
    // ── 排序按钮 ──
    final int sortX, sortY;
    // ── 分类面板 ──
    final int categoryX, categoryY, categoryH;
    // ── 存储区 ──
    final int storageX, storageY, storageW;
    // ── 合成面板 ──
    final int craftPanelX, craftPanelY, craftPanelH;
    // ── 主存储宽度（不含合成面板）──
    final int mainStorageW;
    // ── 搜索框区域宽度 ──
    final int searchW;
    // ── 分页器 ──
    final int pagerX;
    // ── 工具行 ──
    final int toolY;
    // ── 网格 ──
    final int gridY, gridH;
    // ── 可见存储行数 ──
    final int storageRows;

    BottomPanelLayout(int panelX, int panelY, int panelW, int panelH, int contentX, int contentY, int sortX, int sortY,
        int categoryX, int categoryY, int categoryH, int storageX, int storageY, int storageW, int craftPanelX,
        int craftPanelY, int craftPanelH, int mainStorageW, int searchW, int pagerX, int toolY, int gridY, int gridH,
        int storageRows) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.contentX = contentX;
        this.contentY = contentY;
        this.sortX = sortX;
        this.sortY = sortY;
        this.categoryX = categoryX;
        this.categoryY = categoryY;
        this.categoryH = categoryH;
        this.storageX = storageX;
        this.storageY = storageY;
        this.storageW = storageW;
        this.craftPanelX = craftPanelX;
        this.craftPanelY = craftPanelY;
        this.craftPanelH = craftPanelH;
        this.mainStorageW = mainStorageW;
        this.searchW = searchW;
        this.pagerX = pagerX;
        this.toolY = toolY;
        this.gridY = gridY;
        this.gridH = gridH;
        this.storageRows = storageRows;
    }

    /** 判断坐标是否在面板边界内 */
    boolean contains(int mouseX, int mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    /** 判断坐标是否在面板头部（标签栏）内 */
    boolean isInsideHeader(int mouseX, int mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelW
            && mouseY >= panelY
            && mouseY <= panelY + RtsBottomPanel.HEADER_H;
    }

    /** 转为 AWT Rectangle（供 IRtsPanel.getBounds()） */
    Rectangle toRectangle() {
        return new Rectangle(panelX, panelY, panelW, panelH);
    }
}
