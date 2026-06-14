package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.InteractionViewModel.GuiBindingEntry;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

/**
 * GUI 绑定视图 — 显示所有按键→物品/操作的绑定列表。
 * 仅当 bindingEditMode=true 时可见。
 */
public class GuiBindingView implements IRtsPanel {

    private static final String PANEL_NAME = "gui_bindings";
    private static final int ENTRY_HEIGHT = 16;
    private static final int MAX_VISIBLE = 12;
    private static final int TOP_OFFSET = 55;

    private final RtsClientState state;
    private int bindX, bindY, bindH;

    public GuiBindingView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(bindX, bindY, 280, bindH);
    }

    @Override
    public boolean isVisible() {
        return state.interaction.bindingEditMode;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) return;

        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;
        List<GuiBindingEntry> bindings = ivm.bindings;

        bindX = 10;
        bindY = TOP_OFFSET;
        bindH = Math.min(bindings.size(), MAX_VISIBLE) * ENTRY_HEIGHT;

        fr.drawString("GUI Bindings (edit mode)", bindX, bindY - 12, 0xFFCCCC44);

        int visible = Math.min(bindings.size(), MAX_VISIBLE);
        for (int i = 0; i < visible; i++) {
            GuiBindingEntry entry = bindings.get(i);
            int ey = bindY + i * ENTRY_HEIGHT;
            Gui.drawRect(bindX, ey, bindX + 280, ey + ENTRY_HEIGHT, 0x88444444);
            String line = String.format("%s → %s [%s]", entry.keyName, entry.actionName, entry.boundItemId);
            fr.drawString(line, bindX + 4, ey + 3, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
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
}
