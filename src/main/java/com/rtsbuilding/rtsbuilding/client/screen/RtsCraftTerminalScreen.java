package com.rtsbuilding.rtsbuilding.client.screen;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePageMessage;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;

/**
 * RTS Craft Terminal Screen — vanilla crafting table (left) + linked storage browser (right).
 * Based on the original RTSbuilding CraftTerminalScreen layout.
 */
public class RtsCraftTerminalScreen extends GuiContainer {

    private static final ResourceLocation CRAFTING_TABLE_TEXTURE = new ResourceLocation(
        "textures/gui/container/crafting_table.png");

    private static final int VANILLA_BG_W = 176;
    private static final int VANILLA_BG_H = 166;
    private static final int LINKED_PANEL_W = 166;
    private static final int LINKED_PANEL_H = 158;
    private static final int GAP = 6;

    private static final int LINKED_GRID_COLS = 8;
    private static final int LINKED_GRID_ROWS = 5;
    private static final int LINKED_SLOT_SIZE = 18;
    private static final int LINKED_SLOT_PITCH = 20;

    private final RtsCraftTerminalMenu craftMenu;
    private final RtsClientState state;
    private final StorageViewModel svm;

    public RtsCraftTerminalScreen(Container container) {
        super(container);
        this.craftMenu = (RtsCraftTerminalMenu) container;
        this.state = RtsClientState.get();
        this.svm = state.storage;
        this.xSize = VANILLA_BG_W + GAP + LINKED_PANEL_W;
        this.ySize = VANILLA_BG_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        // Request storage page data for the linked storage browser
        RtsNetworkManager.NETWORK.sendToServer(new C2SRtsRequestStoragePageMessage(0, 0, svm.sortMode));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Render vanilla crafting table background (left side)
        mc.getTextureManager()
            .bindTexture(CRAFTING_TABLE_TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, VANILLA_BG_W, VANILLA_BG_H);

        // Render linked storage browser background (right side)
        int linkedX = guiLeft + VANILLA_BG_W + GAP;
        int linkedY = guiTop + 4;
        drawRect(linkedX, linkedY, linkedX + LINKED_PANEL_W, linkedY + LINKED_PANEL_H, 0xCC1A1A2E);

        // Render linked storage header
        drawRect(linkedX, linkedY, linkedX + LINKED_PANEL_W, linkedY + 16, 0xCC2A2A3E);
        FontRenderer fr = mc.fontRenderer;
        drawString(fr, "\u00a7eLinked Storage", linkedX + 4, linkedY + 4, 0xFFFFCC44);

        // Render page info
        String pageInfo = (svm.currentPage + 1) + "/" + Math.max(1, svm.totalPages);
        drawString(fr, pageInfo, linkedX + LINKED_PANEL_W - fr.getStringWidth(pageInfo) - 4, linkedY + 4, 0xFFAAAAAA);

        // Render linked storage items (8x5 grid)
        int gridX = linkedX + 4;
        int gridY = linkedY + 20;
        List<StorageViewModel.StorageEntry> entries = svm.getDisplayEntries();

        for (int row = 0; row < LINKED_GRID_ROWS; row++) {
            for (int col = 0; col < LINKED_GRID_COLS; col++) {
                int index = row * LINKED_GRID_COLS + col;
                int slotX = gridX + col * LINKED_SLOT_PITCH;
                int slotY = gridY + row * LINKED_SLOT_PITCH;

                // Draw slot background
                boolean hover = mouseX >= slotX && mouseX < slotX + LINKED_SLOT_SIZE
                    && mouseY >= slotY
                    && mouseY < slotY + LINKED_SLOT_SIZE;
                int bgColor = hover ? 0x88666666 : 0x88444444;
                drawRect(slotX, slotY, slotX + LINKED_SLOT_SIZE, slotY + LINKED_SLOT_SIZE, bgColor);

                // Draw item if present
                if (index < entries.size()) {
                    StorageViewModel.StorageEntry entry = entries.get(index);
                    ItemStack stack = svm.resolveStack(entry.itemId, entry.meta);
                    if (stack != null) {
                        renderLinkedItem(stack, slotX + 1, slotY + 1, entry.count);
                    }
                }
            }
        }

        // Render page navigation buttons
        int btnY = gridY + LINKED_GRID_ROWS * LINKED_SLOT_PITCH + 4;
        drawRect(linkedX + 4, btnY, linkedX + 30, btnY + 12, 0xFF555577);
        drawString(fr, "<", linkedX + 13, btnY + 2, 0xFFDDDDDD);
        drawRect(linkedX + LINKED_PANEL_W - 30, btnY, linkedX + LINKED_PANEL_W - 4, btnY + 12, 0xFF555577);
        drawString(fr, ">", linkedX + LINKED_PANEL_W - 21, btnY + 2, 0xFFDDDDDD);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Title
        drawString(mc.fontRenderer, "\u00a76RTS Craft Terminal", 28, 6, 0xFFFFCC44);

        // Player inventory label
        drawString(mc.fontRenderer, "Inventory", 8, 72, 0xFF404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Render tooltips for linked storage items
        int linkedX = guiLeft + VANILLA_BG_W + GAP;
        int linkedY = guiTop + 4;
        int gridX = linkedX + 4;
        int gridY = linkedY + 20;
        List<StorageViewModel.StorageEntry> entries = svm.getDisplayEntries();

        for (int row = 0; row < LINKED_GRID_ROWS; row++) {
            for (int col = 0; col < LINKED_GRID_COLS; col++) {
                int index = row * LINKED_GRID_COLS + col;
                int slotX = gridX + col * LINKED_SLOT_PITCH;
                int slotY = gridY + row * LINKED_SLOT_PITCH;

                if (index < entries.size() && mouseX >= slotX
                    && mouseX < slotX + LINKED_SLOT_SIZE
                    && mouseY >= slotY
                    && mouseY < slotY + LINKED_SLOT_SIZE) {
                    StorageViewModel.StorageEntry entry = entries.get(index);
                    ItemStack stack = svm.resolveStack(entry.itemId, entry.meta);
                    if (stack != null) {
                        drawHoveringText(
                            java.util.Arrays.asList(stack.getDisplayName(), "\u00a77" + entry.count + " items"),
                            mouseX,
                            mouseY,
                            mc.fontRenderer);
                    }
                }
            }
        }
    }

    private void renderLinkedItem(ItemStack stack, int x, int y, long count) {
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem ri = RenderItem.getInstance();
        ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);

        // Render count (bottom-right corner)
        String countStr = count > 9999 ? String.format("%.1fk", count / 1000.0) : String.valueOf(count);
        int countX = x + LINKED_SLOT_SIZE - 2 - mc.fontRenderer.getStringWidth(countStr);
        int countY = y + LINKED_SLOT_SIZE - 8;
        mc.fontRenderer.drawStringWithShadow(countStr, countX, countY, 0xFFFFFF);

        RenderHelper.disableStandardItemLighting();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        // Check if click is in linked storage area
        int linkedX = guiLeft + VANILLA_BG_W + GAP;
        int linkedY = guiTop + 4;
        int gridX = linkedX + 4;
        int gridY = linkedY + 20;

        if (mouseX >= linkedX && mouseX < linkedX + LINKED_PANEL_W
            && mouseY >= linkedY
            && mouseY < linkedY + LINKED_PANEL_H) {

            // Check page navigation buttons
            int btnY = gridY + LINKED_GRID_ROWS * LINKED_SLOT_PITCH + 4;
            if (mouseY >= btnY && mouseY < btnY + 12) {
                if (mouseX >= linkedX + 4 && mouseX < linkedX + 30) {
                    // Previous page
                    if (svm.currentPage > 0) {
                        svm.currentPage--;
                        RtsNetworkManager.NETWORK
                            .sendToServer(new C2SRtsRequestStoragePageMessage(svm.currentPage, 0, svm.sortMode));
                    }
                    return;
                }
                if (mouseX >= linkedX + LINKED_PANEL_W - 30 && mouseX < linkedX + LINKED_PANEL_W - 4) {
                    // Next page
                    if (svm.currentPage < svm.totalPages - 1) {
                        svm.currentPage++;
                        RtsNetworkManager.NETWORK
                            .sendToServer(new C2SRtsRequestStoragePageMessage(svm.currentPage, 0, svm.sortMode));
                    }
                    return;
                }
            }

            // Check grid clicks
            List<StorageViewModel.StorageEntry> entries = svm.getDisplayEntries();
            for (int row = 0; row < LINKED_GRID_ROWS; row++) {
                for (int col = 0; col < LINKED_GRID_COLS; col++) {
                    int index = row * LINKED_GRID_COLS + col;
                    int slotX = gridX + col * LINKED_SLOT_PITCH;
                    int slotY = gridY + row * LINKED_SLOT_PITCH;

                    if (index < entries.size() && mouseX >= slotX
                        && mouseX < slotX + LINKED_SLOT_SIZE
                        && mouseY >= slotY
                        && mouseY < slotY + LINKED_SLOT_SIZE) {
                        // Linked storage item clicked — pick up into cursor
                        StorageViewModel.StorageEntry entry = entries.get(index);
                        ItemStack stack = svm.resolveStack(entry.itemId, entry.meta);
                        if (stack != null) {
                            state.interaction.selectedBlockId = entry.itemId;
                            state.interaction.selectedBlockMeta = entry.meta;
                            RtsNetworkManager.NETWORK.sendToServer(
                                new com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupMessage(0, 0));
                        }
                        return;
                    }
                }
            }
            return; // Consumed click in linked area
        }

        // Fall through to normal Container click handling
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
