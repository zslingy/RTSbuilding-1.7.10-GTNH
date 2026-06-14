package com.rtsbuilding.rtsbuilding.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;

/**
 * RTS 技能树界面 — 进度节点展示。
 */
public class RtsProgressionScreen extends GuiScreen {

    private final RtsClientState state;
    private static final int NODE_SIZE = 14;
    private static final int GRID_COLS = 6;
    private static final int GRID_ROWS = 4;
    private int hoveredNodeIdx = -1;

    public RtsProgressionScreen() {
        this.state = RtsClientState.get();
        this.hoveredNodeIdx = -1;
    }

    @Override
    public void initGui() {}

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("screen.rtsbuilding.progression_skill_tree"),
            width / 2,
            16,
            0xFFFFCC44);

        int startX = (width - GRID_COLS * 80) / 2 + 40;
        int startY = 50;

        RtsFeature[] features = RtsFeature.values();
        int totalNodes = Math.min(features.length, GRID_COLS * GRID_ROWS);
        int unlocked = 0;

        for (int idx = 0; idx < totalNodes; idx++) {
            int col = idx % GRID_COLS;
            int row = idx / GRID_COLS;
            int cx = startX + col * 80;
            int cy = startY + row * 80;

            RtsFeature feature = features[idx];
            boolean isUnlocked = state.progression.isNodeUnlocked(feature.name());

            if (isUnlocked) unlocked++;

            int bgColor = isUnlocked ? 0xCC44AA44 : 0xCC442222;
            Gui.drawRect(cx - NODE_SIZE, cy - NODE_SIZE, cx + NODE_SIZE, cy + NODE_SIZE, bgColor);

            int borderColor = isHover(mouseX, mouseY, cx, cy, NODE_SIZE) ? 0xFFFFFFFF : 0xCC888888;
            Gui.drawRect(cx - NODE_SIZE - 1, cy - NODE_SIZE - 1, cx - NODE_SIZE, cy + NODE_SIZE + 1, borderColor);
            Gui.drawRect(cx + NODE_SIZE, cy - NODE_SIZE - 1, cx + NODE_SIZE + 1, cy + NODE_SIZE + 1, borderColor);
            Gui.drawRect(cx - NODE_SIZE, cy - NODE_SIZE - 1, cx + NODE_SIZE, cy - NODE_SIZE, borderColor);
            Gui.drawRect(cx - NODE_SIZE, cy + NODE_SIZE, cx + NODE_SIZE, cy + NODE_SIZE + 1, borderColor);

            String label = feature.name();
            int labelColor = isUnlocked ? 0xFFDDDDDD : 0xFF888888;
            drawCenteredString(fontRendererObj, label, cx, cy + NODE_SIZE + 6, labelColor);

            if (col < GRID_COLS - 1 && idx + 1 < totalNodes) {
                int nextCx = cx + 80;
                int lineColor = (isUnlocked && state.progression.isNodeUnlocked(features[idx + 1].name())) ? 0xCC44AA44
                    : 0xCC333333;
                Gui.drawRect(cx + NODE_SIZE, cy - 1, nextCx - NODE_SIZE, cy + 1, lineColor);
            }
        }

        hoveredNodeIdx = -1;
        for (int idx = 0; idx < totalNodes; idx++) {
            int col = idx % GRID_COLS;
            int row = idx / GRID_COLS;
            int cx = startX + col * 80;
            int cy = startY + row * 80;
            if (isHover(mouseX, mouseY, cx, cy, NODE_SIZE + 4)) {
                hoveredNodeIdx = idx;
                break;
            }
        }

        String progress = StatCollector
            .translateToLocalFormatted("screen.rtsbuilding.home_screen.nodes_unlocked", unlocked, totalNodes);
        drawCenteredString(fontRendererObj, progress, width / 2, startY + GRID_ROWS * 80 + 20, 0xFFAAAAAA);

        String hint = StatCollector.translateToLocal("screen.rtsbuilding.progression.click_hint");
        drawCenteredString(fontRendererObj, hint, width / 2, height - 20, 0xFF666666);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        if (hoveredNodeIdx < 0) return;
        RtsFeature[] features = RtsFeature.values();
        if (hoveredNodeIdx >= features.length) return;

        RtsFeature feature = features[hoveredNodeIdx];
        boolean isUnlocked = state.progression.isNodeUnlocked(feature.name());
        drawNodeDetailPopup(feature, isUnlocked, mouseX, mouseY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean isHover(int mx, int my, int cx, int cy, int nodeSize) {
        return mx >= cx - nodeSize && mx <= cx + nodeSize && my >= cy - nodeSize && my <= cy + nodeSize;
    }

    private void drawNodeDetailPopup(RtsFeature feature, boolean isUnlocked, int mx, int my) {
        String name = feature.name();
        List<String> lines = new ArrayList<>();
        lines.add((isUnlocked ? "\u00a7a" : "\u00a7c") + name);
        lines.add(isUnlocked ? "\u00a77Status: Unlocked" : "\u00a77Status: Locked");

        String desc = getFeatureDescription(feature);
        if (!desc.isEmpty()) {
            lines.add("\u00a7f" + desc);
        }

        int maxW = 0;
        for (String l : lines) {
            int w = fontRendererObj.getStringWidth(l);
            if (w > maxW) maxW = w;
        }

        int popupW = maxW + 16;
        int popupH = lines.size() * 12 + 12;
        int popupX = mx + 12;
        int popupY = my - popupH / 2;

        if (popupX + popupW > width) popupX = mx - popupW - 8;
        if (popupY < 4) popupY = 4;
        if (popupY + popupH > height) popupY = height - popupH - 4;

        Gui.drawRect(popupX, popupY, popupX + popupW, popupY + popupH, 0xCC222222);
        for (int i = 0; i < lines.size(); i++) {
            fontRendererObj.drawString(lines.get(i), popupX + 6, popupY + 6 + i * 12, 0xFFFFFFFF);
        }
    }

    /** 根据 feature 推断描述 */
    private static String getFeatureDescription(RtsFeature feature) {
        switch (feature) {
            case CAMERA:
                return "Deploy RTS camera in the field";
            case REMOTE_PLACE:
                return "Place blocks remotely";
            case REMOTE_BREAK:
                return "Break blocks remotely";
            case AREA_DESTROY:
                return "Destroy blocks in an area";
            case LINK_STORAGE:
                return "Link storage containers";
            case BLUEPRINTS:
                return "Load and place blueprints";
            default:
                return "";
        }
    }
}
