package com.rtsbuilding.rtsbuilding.client.panel;

import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.blueprint.network.C2SBlueprintPlaceMessage;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;

public class BlueprintPanel implements IRtsPanel {

    private static final String PANEL_NAME = "blueprint_panel";
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 260;
    private static final int SCROLL_MAX = 10;

    private final RtsClientState state;
    private int bpX, bpY;
    private boolean visible = false;
    private int scrollOffset = 0;
    private List<String> blueprintFiles = new ArrayList<>();
    private int selectedIndex = -1;
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL_MS = 30000;
    private int rotateSteps = 0;

    private static final String[] ROTATE_LABELS = { "N", "W", "S", "E" };

    public BlueprintPanel() {
        this.state = RtsClientState.get();
        refreshBlueprintList();
    }

    @Override
    public String panelName() {
        return PANEL_NAME;
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(bpX, bpY, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void toggleVisibility() {
        visible = !visible;
        if (visible) refreshBlueprintList();
    }

    @Override
    public void render(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        FontRenderer fr = screen.mc.fontRenderer;
        bpX = screen.width - PANEL_WIDTH - 10;
        bpY = 30;

        Gui.drawRect(bpX, bpY, bpX + PANEL_WIDTH, bpY + PANEL_HEIGHT, 0xCC1A1A2E);
        Gui.drawRect(bpX + 1, bpY + 1, bpX + PANEL_WIDTH - 1, bpY + PANEL_HEIGHT - 1, 0xCC2A2A3E);

        drawCenteredString(fr, "Blueprints", bpX + PANEL_WIDTH / 2, bpY + 6, 0xFFFFCC44);

        // Refresh button (top-right)
        int refreshX = bpX + PANEL_WIDTH - 58;
        int refreshColor = isHover(mouseX, mouseY, refreshX, bpY + 4, 50, 14) ? 0xCC556688 : 0xCC334466;
        Gui.drawRect(refreshX, bpY + 4, refreshX + 50, bpY + 18, refreshColor);
        drawCenteredString(fr, "Refresh", refreshX + 25, bpY + 6, 0xFFDDDDDD);

        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > REFRESH_INTERVAL_MS) {
            refreshBlueprintList();
            lastRefreshTime = now;
        }

        int listY = bpY + 24;
        int itemHeight = 16;
        int maxVisible = Math.min(blueprintFiles.size() - scrollOffset, SCROLL_MAX);

        for (int i = 0; i < maxVisible; i++) {
            int idx = scrollOffset + i;
            if (idx >= blueprintFiles.size()) break;

            String fileName = blueprintFiles.get(idx);
            int itemY = listY + i * itemHeight;

            int bgColor = (idx == selectedIndex) ? 0xCC446688 : 0xCC333344;
            Gui.drawRect(bpX + 6, itemY, bpX + PANEL_WIDTH - 6, itemY + itemHeight, bgColor);

            String displayName = fileName.length() > 26 ? fileName.substring(0, 23) + "..." : fileName;
            fr.drawString(displayName, bpX + 10, itemY + 3, 0xFFDDDDDD);

            BlueprintFormat fmt = BlueprintFormat.fromFileName(fileName);
            String fmtLabel = fmt.name()
                .substring(
                    0,
                    Math.min(
                        4,
                        fmt.name()
                            .length()));
            int fmtColor = fmt == BlueprintFormat.SPONGE_SCHEM ? 0xFF88BB88 : 0xFF8888BB;
            fr.drawString(fmtLabel, bpX + PANEL_WIDTH - 30, itemY + 3, fmtColor);
        }

        if (blueprintFiles.size() > SCROLL_MAX) {
            int totalPages = (blueprintFiles.size() + SCROLL_MAX - 1) / SCROLL_MAX;
            int currentPage = scrollOffset / SCROLL_MAX + 1;
            String pageInfo = currentPage + "/" + totalPages;
            fr.drawString(pageInfo, bpX + PANEL_WIDTH - 40, bpY + PANEL_HEIGHT - 52, 0xFF666666);
        }

        if (blueprintFiles.isEmpty()) {
            drawCenteredString(fr, "No blueprints found", bpX + PANEL_WIDTH / 2, bpY + 60, 0xFF666666);
            drawCenteredString(fr, "Place files in:", bpX + PANEL_WIDTH / 2, bpY + 78, 0xFF666666);
            drawCenteredString(fr, "rtsbuilding-blueprints/", bpX + PANEL_WIDTH / 2, bpY + 92, 0xFF888888);
        }

        // Rotation bar
        if (selectedIndex >= 0) {
            int rotateY = bpY + PANEL_HEIGHT - 44;
            fr.drawString("Rot:", bpX + 10, rotateY + 2, 0xFFAAAAAA);

            int rotBtnSize = 24;
            for (int i = 0; i < 4; i++) {
                int rx = bpX + 40 + i * (rotBtnSize + 4);
                int rotColor = (rotateSteps == i) ? 0xCC6699CC : 0xCC444466;
                if (isHover(mouseX, mouseY, rx, rotateY, rotBtnSize, 16)) {
                    rotColor = 0xCC7799DD;
                }
                Gui.drawRect(rx, rotateY, rx + rotBtnSize, rotateY + 16, rotColor);
                drawCenteredString(fr, ROTATE_LABELS[i], rx + rotBtnSize / 2, rotateY + 3, 0xFFFFFFFF);
            }
        }

        // Bottom buttons: Place / Close
        int btnWidth = 60;
        int btnY = bpY + PANEL_HEIGHT - 22;
        int placedBtnX = bpX + 10;
        int closeBtnX = bpX + PANEL_WIDTH - btnWidth - 10;

        int placeColor = (selectedIndex >= 0 && isHover(mouseX, mouseY, placedBtnX, btnY, btnWidth, 16)) ? 0xCC6699CC
            : 0xCC446688;
        Gui.drawRect(placedBtnX, btnY, placedBtnX + btnWidth, btnY + 16, placeColor);
        drawCenteredString(fr, "Place", placedBtnX + btnWidth / 2, btnY + 3, 0xFFFFFFFF);

        int closeColor = isHover(mouseX, mouseY, closeBtnX, btnY, btnWidth, 16) ? 0xCC996666 : 0xCC664444;
        Gui.drawRect(closeBtnX, btnY, closeBtnX + btnWidth, btnY + 16, closeColor);
        drawCenteredString(fr, "Close", closeBtnX + btnWidth / 2, btnY + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0) return false;

        // Refresh button
        int refreshX = bpX + PANEL_WIDTH - 58;
        if (isHover(mouseX, mouseY, refreshX, bpY + 4, 50, 14)) {
            refreshBlueprintList();
            return true;
        }

        // Place / Close buttons
        int btnY = bpY + PANEL_HEIGHT - 22;
        int placedBtnX = bpX + 10;
        int closeBtnX = bpX + PANEL_WIDTH - 70;

        if (isHover(mouseX, mouseY, placedBtnX, btnY, 60, 16)) {
            placeSelected();
            return true;
        }
        if (isHover(mouseX, mouseY, closeBtnX, btnY, 60, 16)) {
            visible = false;
            return true;
        }

        // Rotation buttons
        if (selectedIndex >= 0) {
            int rotateY = bpY + PANEL_HEIGHT - 44;
            int rotBtnSize = 24;
            for (int i = 0; i < 4; i++) {
                int rx = bpX + 40 + i * (rotBtnSize + 4);
                if (isHover(mouseX, mouseY, rx, rotateY, rotBtnSize, 16)) {
                    rotateSteps = i;
                    return true;
                }
            }
        }

        // Blueprint list items
        int listY = bpY + 24;
        int itemHeight = 16;
        int maxVisible = Math.min(blueprintFiles.size() - scrollOffset, SCROLL_MAX);
        for (int i = 0; i < maxVisible; i++) {
            int itemY = listY + i * itemHeight;
            if (isHover(mouseX, mouseY, bpX + 6, itemY, PANEL_WIDTH - 12, itemHeight)) {
                selectedIndex = scrollOffset + i;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, int scroll) {
        if (!visible) return false;
        if (blueprintFiles.size() > SCROLL_MAX) {
            scrollOffset = Math
                .max(0, Math.min(scrollOffset - Integer.signum(scroll), blueprintFiles.size() - SCROLL_MAX));
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keyCode) {
        if (!visible) return false;
        if (keyCode == Keyboard.KEY_R && selectedIndex >= 0) {
            rotateSteps = BlueprintTransform.normalizeSteps(rotateSteps + 1);
            return true;
        }
        return false;
    }

    @Override
    public void resetFrameState() {}

    private void placeSelected() {
        if (selectedIndex < 0 || selectedIndex >= blueprintFiles.size()) return;
        String fileName = blueprintFiles.get(selectedIndex);
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.entity.Entity viewEntity = mc.renderViewEntity;
        int anchorX = (int) Math.floor(viewEntity.posX);
        int anchorY = (int) Math.floor(viewEntity.posY);
        int anchorZ = (int) Math.floor(viewEntity.posZ);
        RtsNetworkManager.NETWORK
            .sendToServer(new C2SBlueprintPlaceMessage(fileName, anchorX, anchorY, anchorZ, (byte) rotateSteps));
    }

    private void refreshBlueprintList() {
        blueprintFiles.clear();
        try {
            Minecraft mc = Minecraft.getMinecraft();
            File blueprintsDir = new File(mc.mcDataDir, "rtsbuilding-blueprints");
            if (blueprintsDir.exists() && blueprintsDir.isDirectory()) {
                File[] files = blueprintsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            String name = f.getName()
                                .toLowerCase(java.util.Locale.ROOT);
                            if (name.endsWith(".nbt") || name.endsWith(".schem")
                                || name.endsWith(".schematic")
                                || name.endsWith(".litematic")
                                || name.endsWith(".json")) {
                                blueprintFiles.add(f.getName());
                            }
                        }
                    }
                }
            }
            java.util.Collections.sort(blueprintFiles);
        } catch (Exception ignored) {}
        lastRefreshTime = System.currentTimeMillis();
    }

    private static boolean isHover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void drawCenteredString(FontRenderer fr, String text, int x, int y, int color) {
        fr.drawString(text, x - fr.getStringWidth(text) / 2, y, color);
    }
}
