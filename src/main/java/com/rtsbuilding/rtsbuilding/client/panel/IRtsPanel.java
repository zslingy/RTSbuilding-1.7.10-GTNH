package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;

import net.minecraft.client.gui.GuiScreen;

/**
 * RTS 面板协议 — 所有 UI 面板实现此接口。
 * 
 * 替代原 BuilderScreen 中 10+ 层 if-else 瀑布式分发，
 * 面板只需声明自己的交互区域，输入路由器自动处理 hit-test。
 */
public interface IRtsPanel {

    /** 面板名称，用于识别和 Z-序排序 */
    String panelName();

    /** 面板在当前屏幕上的矩形边界，用于 hit-test */
    Rectangle getBounds();

    /** 渲染面板内容 */
    void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks);

    /** 鼠标点击 hit-test：返回 true 表示消费了点击 */
    boolean onMouseClick(int mouseX, int mouseY, int button);

    /** 鼠标滚轮：返回 true 表示消费了滚动 */
    boolean onMouseScroll(int mouseX, int mouseY, int scroll);

    /** 键盘输入（仅在面板"聚焦"时调用）：返回 true 表示消费了按键 */
    boolean onKeyTyped(char c, int keyCode);

    /** 面板是否可见/可交互 */
    boolean isVisible();

    /** 帧级重置（在每帧 render 开始时调用，清除上一帧的 hover 状态等） */
    void resetFrameState();
}
