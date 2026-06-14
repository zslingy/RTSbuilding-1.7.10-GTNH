package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;

/**
 * 输入路由器 — 统一处理鼠标/键盘/滚轮分发。
 * 
 * 替代原 BuilderScreen 中 10+ 层 if-else 瀑布式分发。
 * 按面板 Z-序从上层到下层遍历，第一个消费输入的面板获胜。
 */
public class RtsInputRouter {

    /** 按 Z-序排列的面板列表（后添加 = 更上层） */
    private final List<IRtsPanel> panels = new ArrayList<>();

    public RtsInputRouter() {}

    /** 注册面板到路由器 */
    public void registerPanel(IRtsPanel panel) {
        panels.add(panel);
    }

    /** 按名称移除面板 */
    public void unregisterPanel(String panelName) {
        panels.removeIf(
            p -> p.panelName()
                .equals(panelName));
    }

    /** 清空所有面板 */
    public void clearPanels() {
        panels.clear();
    }

    /**
     * 分发鼠标点击事件。
     * 从上层到底层遍历面板，第一个 hit-test 通过并消费的面板获胜。
     * 
     * @return true 如果点击被某个面板消费
     */
    public boolean dispatchClick(int mouseX, int mouseY, int button) {
        // 从上层（列表末尾）到下层（列表开头）遍历
        for (int i = panels.size() - 1; i >= 0; i--) {
            IRtsPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.getBounds()
                .contains(mouseX, mouseY)) {
                if (panel.onMouseClick(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        // 面板未消费 → 交由世界交互处理（阶段 2 实现）
        return false;
    }

    /**
     * 分发鼠标滚轮事件。
     * 
     * @return true 如果滚轮被消费
     */
    public boolean dispatchScroll(int mouseX, int mouseY, int scroll) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            IRtsPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.getBounds()
                .contains(mouseX, mouseY)) {
                if (panel.onMouseScroll(mouseX, mouseY, scroll)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 分发键盘按键事件。
     * 仅传递给"聚焦"的面板（目前简化实现：传递给第一个可见面板）。
     * 完整实现将在后续阶段添加面板焦点管理。
     * 
     * @return true 如果按键被消费
     */
    public boolean dispatchKey(char c, int keyCode) {
        for (IRtsPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.onKeyTyped(c, keyCode)) {
                return true;
            }
        }
        return false;
    }

    /** 对所有面板调用帧级重置 */
    public void resetAllFrames() {
        for (IRtsPanel panel : panels) {
            panel.resetFrameState();
        }
    }
}
