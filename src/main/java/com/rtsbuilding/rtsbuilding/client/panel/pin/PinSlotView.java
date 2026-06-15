package com.rtsbuilding.rtsbuilding.client.panel.pin;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.panel.IRtsPanel;
import com.rtsbuilding.rtsbuilding.client.panel.RtsBottomPanel;

import cpw.mods.fml.common.registry.GameData;

/**
 * 钉选槽位视图 — 固定显示若干钉选的物品槽位。
 * 位于底部面板固定区域，用于常用物品快速访问。
 */
public class PinSlotView implements IRtsPanel {

    private static final String PANEL_NAME = "pin_slots";
    private static final int MAX_PINS = 8;
    private static final int SLOT_SIZE = 10;
    private static final int TOP_OFFSET = 240;

    private final RtsClientState state;
    private final RenderItem renderItem = new RenderItem();
    private final String[] pinnedItemIds = new String[MAX_PINS];
    private final int[] pinnedItemMetas = new int[MAX_PINS];

    private int pinX, pinY, pinW;
    private int hoveredPin = -1;

    public PinSlotView() {
        this.state = RtsClientState.get();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(pinX, pinY, pinW, SLOT_SIZE);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        // 独立面板模式：从 RtsBottomPanel 获取坐标
        RtsBottomPanel bp = (RtsBottomPanel) ((RtsScreen) screen).getPanel("bottom_panel");
        int gridBottom = bp.getStorageY() + bp.getGridH();
        pinX = bp.getStorageX();
        pinY = gridBottom + 4;
        pinW = MAX_PINS * SLOT_SIZE;
        renderAt(screen, pinX, pinY, mouseX, mouseY);
    }

    /**
     * 由 RtsBottomPanel 调用，传入计算好的坐标。
     * 位于工具区右侧（hotbar + 空手按钮之后 + 12px 间距）。
     */
    public void renderAt(GuiScreen screen, int x, int y, int mouseX, int mouseY) {
        this.pinX = x;
        this.pinY = y;
        this.pinW = MAX_PINS * SLOT_SIZE;

        FontRenderer fr = screen.mc.fontRenderer;
        Minecraft mc = screen.mc;

        for (int i = 0; i < MAX_PINS; i++) {
            int sx = pinX + i * SLOT_SIZE;
            int sy = pinY;

            int bgColor = (i == hoveredPin) ? 0x88666666 : 0x88333333;
            Gui.drawRect(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, bgColor);
            Gui.drawRect(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF5E6874);
            Gui.drawRect(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF0C0D10);
            Gui.drawRect(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF5E6874);
            Gui.drawRect(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF0C0D10);

            if (pinnedItemIds[i] != null && !pinnedItemIds[i].isEmpty()) {
                ItemStack stack = resolveStack(pinnedItemIds[i], pinnedItemMetas[i]);
                if (stack != null) {
                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_BLEND);
                    RenderHelper.enableGUIStandardItemLighting();
                    renderItem.renderItemAndEffectIntoGUI(fr, mc.renderEngine, stack, sx + 2, sy + 2);
                    renderItem.renderItemOverlayIntoGUI(fr, mc.renderEngine, stack, sx + 2, sy + 2);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glPopMatrix();
                }
            }

            // hover 高亮
            if (mouseX >= sx && mouseX <= sx + SLOT_SIZE && mouseY >= sy && mouseY <= sy + SLOT_SIZE) {
                hoveredPin = i;
            }
        }
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        int col = (mouseX - pinX) / SLOT_SIZE;
        if (col >= 0 && col < MAX_PINS && mouseY >= pinY && mouseY <= pinY + SLOT_SIZE) {
            if (button == 0) {
                // 左键：选中钉选物品
                if (pinnedItemIds[col] != null && !pinnedItemIds[col].isEmpty()) {
                    state.interaction.selectedBlockId = pinnedItemIds[col];
                    state.interaction.selectedBlockMeta = pinnedItemMetas[col];
                    return true;
                }
            } else if (button == 1) {
                // 右键：从当前选中物品钉选
                if (state.interaction.selectedBlockId != null && !state.interaction.selectedBlockId.isEmpty()) {
                    pinnedItemIds[col] = state.interaction.selectedBlockId;
                    pinnedItemMetas[col] = state.interaction.selectedBlockMeta;
                    return true;
                }
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
    public void resetFrameState() {
        hoveredPin = -1;
    }

    private ItemStack resolveStack(String itemId, int meta) {
        if (itemId == null) return null;
        try {
            Item item = (Item) GameData.getItemRegistry()
                .getObject(new ResourceLocation(itemId));
            if (item != null) return new ItemStack(item, 1, meta);
        } catch (Exception ignored) {}
        return null;
    }
}
