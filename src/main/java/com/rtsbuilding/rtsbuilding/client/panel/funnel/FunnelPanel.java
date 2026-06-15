package com.rtsbuilding.rtsbuilding.client.panel.funnel;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelMessage;

/** 漏斗设置面板：调整鼠标目标周围的掉落物吸取范围。 */
public class FunnelPanel extends RtsWindowPanel {

    private static final int PANEL_W = 170;
    private static final int PANEL_H = 82;
    private static final int ROW_H = 14;

    private final RtsClientState state;
    private boolean sliderDragging;

    public FunnelPanel() {
        this.state = RtsClientState.get();
    }

    @Override
    protected String getWindowId() {
        return "funnel_panel";
    }

    @Override
    protected String getTitle() {
        return "Funnel";
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected boolean canShowWindow() {
        return state.interaction.funnelActive;
    }

    @Override
    protected void renderContent(GuiScreen screen, int mouseX, int mouseY, float partialTicks, int cx, int cy, int cw,
        int ch) {
        InteractionViewModel ivm = state.interaction;
        FontRenderer fr = screen.mc.fontRenderer;
        int x = cx + 4;
        int y = cy + 6;
        int range = clamp(ivm.funnelRangeSize);

        fr.drawString("Range: " + range + "x" + range + "x" + range, x, y, 0xFFFFE36A);
        y += ROW_H + 6;

        int trackX = x;
        int trackW = cw - 12;
        int knobX = trackX + (int) Math.round(
            (range - InteractionViewModel.FUNNEL_MIN_RANGE)
                / (double) (InteractionViewModel.FUNNEL_MAX_RANGE - InteractionViewModel.FUNNEL_MIN_RANGE)
                * trackW);
        boolean hoverSlider = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= y - 4 && mouseY <= y + ROW_H;

        drawRect(trackX, y + 3, trackX + trackW, y + 7, 0xFF0D1117);
        drawRect(trackX + 1, y + 4, trackX + trackW - 1, y + 6, 0xFF5B4B16);
        drawRect(trackX, y + 4, knobX, y + 6, 0xFFFFE36A);

        int knobColor = sliderDragging ? 0xFFFFFF88 : (hoverSlider ? 0xFFFFFF55 : 0xFFFFE36A);
        drawRect(knobX - 3, y - 1, knobX + 4, y + 10, knobColor);

        fr.drawString(String.valueOf(InteractionViewModel.FUNNEL_MIN_RANGE), trackX, y + 11, 0xFFB0C0D0);
        String max = String.valueOf(InteractionViewModel.FUNNEL_MAX_RANGE);
        fr.drawString(max, trackX + trackW - fr.getStringWidth(max), y + 11, 0xFFB0C0D0);
    }

    @Override
    protected void handleContentClick(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        int y = windowY + 16 + 6 + ROW_H + 6;
        int trackX = windowX + 2 + 4;
        int trackW = windowWidth - 4 - 12;
        if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= y - 4 && mouseY <= y + ROW_H) {
            sliderDragging = true;
            updateRangeFromMouse(mouseX);
        }
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY) {
        if (sliderDragging) {
            updateRangeFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY) {
        sliderDragging = false;
        return super.mouseReleased(mouseX, mouseY);
    }

    private void updateRangeFromMouse(int mouseX) {
        int trackX = windowX + 2 + 4;
        int trackW = windowWidth - 4 - 12;
        double fraction = (mouseX - trackX) / (double) Math.max(1, trackW);
        int range = InteractionViewModel.FUNNEL_MIN_RANGE + (int) Math.round(
            Math.max(0.0D, Math.min(1.0D, fraction))
                * (InteractionViewModel.FUNNEL_MAX_RANGE - InteractionViewModel.FUNNEL_MIN_RANGE));
        range = clamp(range);
        if (state.interaction.funnelRangeSize == range) return;
        state.interaction.funnelRangeSize = range;
        sendRangeUpdate();
    }

    private void sendRangeUpdate() {
        InteractionViewModel ivm = state.interaction;
        RtsNetworkManager.NETWORK.sendToServer(
            new C2SRtsSetFunnelMessage(
                0,
                0,
                ivm.funnelTargetX,
                ivm.funnelTargetY,
                ivm.funnelTargetZ,
                ivm.funnelHasTarget,
                ivm.funnelRangeSize));
    }

    private static int clamp(int value) {
        return Math.max(InteractionViewModel.FUNNEL_MIN_RANGE, Math.min(InteractionViewModel.FUNNEL_MAX_RANGE, value));
    }
}
