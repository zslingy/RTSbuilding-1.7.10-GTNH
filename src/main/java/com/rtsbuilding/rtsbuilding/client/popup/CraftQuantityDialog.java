package com.rtsbuilding.rtsbuilding.client.popup;

import java.awt.Rectangle;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.CraftViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipeMessage;

/**
 * 合成数量对话框 — 输入合成数量后确认/取消。
 * 阶段5：确认按钮发送 C2SRtsCraftRecipeMessage 到服务端。
 * 浮动在屏幕中央上方。
 */
public class CraftQuantityDialog implements IRtsPanel {

    private static final String PANEL_NAME = "quantity_dialog";
    private static final int DIALOG_W = 160;
    private static final int DIALOG_H = 80;
    private static final int BUTTON_W = 60;
    private static final int BUTTON_H = 16;

    private final RtsClientState state;
    private int dialogX, dialogY;
    private boolean confirmHover, cancelHover;

    public CraftQuantityDialog() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(dialogX, dialogY, DIALOG_W, DIALOG_H);
    }

    @Override
    public boolean isVisible() {
        return state.craft.quantityDialogOpen;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        CraftViewModel cvm = state.craft;
        FontRenderer fr = screen.mc.fontRenderer;

        // 居中定位
        dialogX = (screen.width - DIALOG_W) / 2;
        dialogY = (screen.height - DIALOG_H) / 2 - 40;

        // 背景
        Gui.drawRect(dialogX, dialogY, dialogX + DIALOG_W, dialogY + DIALOG_H, 0xCC333333);
        Gui.drawRect(dialogX + 2, dialogY + 2, dialogX + DIALOG_W - 2, dialogY + DIALOG_H - 2, 0xCC555555);

        // 标题
        fr.drawString("Craft Quantity", dialogX + 8, dialogY + 6, 0xFFCCCC44);

        // 数量显示
        String countStr = String.valueOf(cvm.quantityDialogCount);
        int countW = fr.getStringWidth(countStr);
        fr.drawString(countStr, dialogX + DIALOG_W / 2 - countW / 2, dialogY + 22, 0xFFFFFFFF);

        // 加减按钮
        String minus = "-";
        String plus = "+";
        int minusX = dialogX + 20;
        int plusX = dialogX + DIALOG_W - 40;
        int btnY = dialogY + 20;
        fr.drawString(minus, minusX, btnY, 0xFFCC4444);
        fr.drawString(plus, plusX, btnY, 0xFF44CC44);

        // 确认/取消按钮
        int btnY2 = dialogY + DIALOG_H - 20;
        int confirmX = dialogX + DIALOG_W - BUTTON_W - 8;
        int cancelX = dialogX + 8;

        int confirmBg = confirmHover ? 0xCC44AA44 : 0xCC448844;
        int cancelBg = cancelHover ? 0xCCAA4444 : 0xCC884444;
        Gui.drawRect(confirmX, btnY2, confirmX + BUTTON_W, btnY2 + BUTTON_H, confirmBg);
        Gui.drawRect(cancelX, btnY2, cancelX + BUTTON_W, btnY2 + BUTTON_H, cancelBg);

        fr.drawString("Craft", confirmX + 12, btnY2 + 3, 0xFFFFFFFF);
        fr.drawString("Cancel", cancelX + 10, btnY2 + 3, 0xFFFFFFFF);

        // 最大数量提示
        String maxHint = "Max: " + cvm.quantityDialogMax;
        fr.drawString(maxHint, dialogX + 8, btnY2 - 10, 0xFFAAAAAA);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!isVisible() || button != 0) return false;

        CraftViewModel cvm = state.craft;

        // 加减按钮
        int minusX = dialogX + 20;
        int plusX = dialogX + DIALOG_W - 40;
        int btnY = dialogY + 20;
        if (mouseX >= minusX && mouseX <= minusX + 10 && mouseY >= btnY && mouseY <= btnY + 10) {
            cvm.quantityDialogCount = Math.max(1, cvm.quantityDialogCount - 1);
            return true;
        }
        if (mouseX >= plusX && mouseX <= plusX + 10 && mouseY >= btnY && mouseY <= btnY + 10) {
            cvm.quantityDialogCount = Math.min(cvm.quantityDialogMax, cvm.quantityDialogCount + 1);
            return true;
        }

        // 确认按钮 — 阶段5：发送 C2S 合成请求
        int btnY2 = dialogY + DIALOG_H - 20;
        int confirmX = dialogX + DIALOG_W - BUTTON_W - 8;
        int cancelX = dialogX + 8;
        if (mouseX >= confirmX && mouseX <= confirmX + BUTTON_W && mouseY >= btnY2 && mouseY <= btnY2 + BUTTON_H) {
            // 从 Panel 中获取 recipeId（CraftPanelView 应在点击时设置）
            // 阶段5联调：使用 dialog 中存储的 itemId 构造 recipeId
            String recipeId = "rts:" + cvm.quantityDialogItemId.replace("minecraft:", "");
            RtsNetworkManager.NETWORK.sendToServer(new C2SRtsCraftRecipeMessage(recipeId, cvm.quantityDialogCount));
            // 对话框关闭（等待服务端 S2C 反馈来确认结果）
            cvm.quantityDialogOpen = false;
            return true;
        }
        if (mouseX >= cancelX && mouseX <= cancelX + BUTTON_W && mouseY >= btnY2 && mouseY <= btnY2 + BUTTON_H) {
            cvm.quantityDialogOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (!isVisible()) return false;
        CraftViewModel cvm = state.craft;
        if (mouseX >= dialogX && mouseX <= dialogX + DIALOG_W && mouseY >= dialogY && mouseY <= dialogY + DIALOG_H) {
            if (scroll > 0) cvm.quantityDialogCount = Math.min(cvm.quantityDialogMax, cvm.quantityDialogCount + 1);
            else cvm.quantityDialogCount = Math.max(1, cvm.quantityDialogCount - 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        if (!isVisible()) return false;
        CraftViewModel cvm = state.craft;
        if (c >= '0' && c <= '9') {
            cvm.quantityDialogCount = c - '0';
            return true;
        }
        if (keyCode == 28) { // Enter
            String recipeId = "rts:" + cvm.quantityDialogItemId.replace("minecraft:", "");
            RtsNetworkManager.NETWORK.sendToServer(new C2SRtsCraftRecipeMessage(recipeId, cvm.quantityDialogCount));
            cvm.quantityDialogOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public void resetFrameState() {
        confirmHover = false;
        cancelHover = false;
    }
}
