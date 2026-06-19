package com.rtsbuilding.rtsbuilding.client.panel.workflow;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.rtsbuilding.rtsbuilding.client.WorkflowViewModel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowMessage;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

public class RtsWorkflowPanel {

    private static final int ITEM_HEIGHT = 22;
    private static final int PANEL_WIDTH = 180;
    public boolean visible;

    public void draw(Minecraft mc, int panelX, int panelY) {
        if (!visible) return;

        List<RtsWorkflowStatus> statuses = WorkflowViewModel.getAllProgress();
        if (statuses.isEmpty()) {
            mc.fontRenderer.drawString("No active workflows", panelX + 8, panelY + 8, 0xAAAAAA);
            return;
        }

        int panelHeight = statuses.size() * ITEM_HEIGHT + 20;
        Gui.drawRect(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xCC222222);
        Gui.drawRect(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFF555555);

        FontRenderer fr = mc.fontRenderer;
        for (int i = 0; i < statuses.size(); i++) {
            RtsWorkflowStatus s = statuses.get(i);
            int itemY = panelY + 10 + i * ITEM_HEIGHT;
            String typeName = s.type() != null ? s.type()
                .name() : "?";
            String text = typeName + " " + s.completedBlocks() + "/" + s.totalBlocks();
            fr.drawString(text, panelX + 8, itemY, 0xCCCCCC);

            float pct = s.totalBlocks() > 0 ? (float) s.completedBlocks() / s.totalBlocks() : 0;
            int barW = PANEL_WIDTH - 16;
            Gui.drawRect(
                panelX + 8,
                itemY + fr.FONT_HEIGHT + 2,
                panelX + 8 + barW,
                itemY + fr.FONT_HEIGHT + 6,
                0xFF333333);
            Gui.drawRect(
                panelX + 8,
                itemY + fr.FONT_HEIGHT + 2,
                panelX + 8 + (int) (barW * pct),
                itemY + fr.FONT_HEIGHT + 6,
                0xFF4488FF);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int panelX, int panelY) {
        if (!visible) return;
        List<RtsWorkflowStatus> statuses = WorkflowViewModel.getAllProgress();
        for (int i = 0; i < statuses.size(); i++) {
            int itemY = panelY + 10 + i * ITEM_HEIGHT;
            if (mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT
                && mouseX >= panelX
                && mouseX <= panelX + PANEL_WIDTH) {
                RtsNetworkManager.NETWORK.sendToServer(
                    new C2SRtsDeleteWorkflowMessage(
                        statuses.get(i)
                            .entryId()));
                break;
            }
        }
    }
}
