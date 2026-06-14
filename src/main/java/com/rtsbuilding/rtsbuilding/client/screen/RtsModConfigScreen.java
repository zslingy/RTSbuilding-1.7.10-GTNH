package com.rtsbuilding.rtsbuilding.client.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

/**
 * RTS Mod 配置界面 — Mod 参数配置。
 * Bug9修复：扩展设置项、关闭时清除 settingsScreenOpen flag。
 */
public class RtsModConfigScreen extends GuiScreen {

    private final GuiScreen parent;
    private static final int BUTTON_ID_RTS_TOGGLE = 0;
    private static final int BUTTON_ID_DEBUG_TOGGLE = 1;
    private static final int BUTTON_ID_BLUEPRINT_TOGGLE = 3;
    private static final int BUTTON_ID_PROGRESSION_TOGGLE = 4;
    private static final int BUTTON_ID_RADIUS_SLIDER = 5;
    private static final int BUTTON_ID_DONE = 2;

    private boolean rtsEnabled;
    private boolean debugMode;
    private boolean blueprintsEnabled;
    private boolean progressionEnabled;
    private int actionRadius;

    public RtsModConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.rtsEnabled = Config.rtsEnabled;
        this.debugMode = Config.debugMode;
        this.blueprintsEnabled = Config.enableBlueprints;
        this.progressionEnabled = Config.enableSurvivalProgression;
        this.actionRadius = Config.maxActionRadiusBlocks;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        int centerX = width / 2;
        int btnY = 50;
        int btnW = 200;
        int spacing = 26;

        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_RTS_TOGGLE,
                centerX - 100,
                btnY,
                btnW,
                20,
                "RTS Mode: " + (rtsEnabled ? "\u00a7aON" : "\u00a7cOFF")));
        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_DEBUG_TOGGLE,
                centerX - 100,
                btnY + spacing,
                btnW,
                20,
                "Debug Mode: " + (debugMode ? "\u00a7aON" : "\u00a7cOFF")));
        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_BLUEPRINT_TOGGLE,
                centerX - 100,
                btnY + spacing * 2,
                btnW,
                20,
                "Blueprints: " + (blueprintsEnabled ? "\u00a7aON" : "\u00a7cOFF")));
        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_PROGRESSION_TOGGLE,
                centerX - 100,
                btnY + spacing * 3,
                btnW,
                20,
                "Progression: " + (progressionEnabled ? "\u00a7aON" : "\u00a7cOFF")));
        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_RADIUS_SLIDER,
                centerX - 100,
                btnY + spacing * 4,
                btnW,
                20,
                "Action Radius: " + actionRadius + " blocks"));
        this.buttonList
            .add(new GuiButton(BUTTON_ID_DONE, centerX - 50, btnY + spacing * 5 + 10, 100, 20, "\u00a7eDone"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_ID_RTS_TOGGLE:
                rtsEnabled = !rtsEnabled;
                Config.rtsEnabled = rtsEnabled;
                button.displayString = "RTS Mode: " + (rtsEnabled ? "\u00a7aON" : "\u00a7cOFF");
                break;
            case BUTTON_ID_DEBUG_TOGGLE:
                debugMode = !debugMode;
                Config.debugMode = debugMode;
                button.displayString = "Debug Mode: " + (debugMode ? "\u00a7aON" : "\u00a7cOFF");
                break;
            case BUTTON_ID_BLUEPRINT_TOGGLE:
                blueprintsEnabled = !blueprintsEnabled;
                Config.enableBlueprints = blueprintsEnabled;
                button.displayString = "Blueprints: " + (blueprintsEnabled ? "\u00a7aON" : "\u00a7cOFF");
                break;
            case BUTTON_ID_PROGRESSION_TOGGLE:
                progressionEnabled = !progressionEnabled;
                Config.enableSurvivalProgression = progressionEnabled;
                button.displayString = "Progression: " + (progressionEnabled ? "\u00a7aON" : "\u00a7cOFF");
                break;
            case BUTTON_ID_RADIUS_SLIDER:
                actionRadius = nextRadius(actionRadius);
                Config.maxActionRadiusBlocks = actionRadius;
                button.displayString = "Action Radius: " + actionRadius + " blocks";
                break;
            case BUTTON_ID_DONE:
                closeSettings();
                break;
        }
    }

    private static int nextRadius(int current) {
        int[] steps = { 48, 64, 96, 128, 192, 256, 384, 512 };
        for (int s : steps) {
            if (s > current) return s;
        }
        return steps[0];
    }

    private void closeSettings() {
        RtsClientState.get().settingsScreenOpen = false;
        mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "\u00a7eRTS Building \u00a77— Configuration", width / 2, 16, 0xFFFFCC44);

        drawCenteredString(fontRendererObj, "\u00a77Changes apply immediately.", width / 2, 34, 0xFF888888);

        String zoomInfo = "UI Zoom: " + (int) (RtsClientState.get().uiZoom * 100) + "%  (adjust in top bar)";
        drawCenteredString(fontRendererObj, "\u00a77" + zoomInfo, width / 2, height - 24, 0xFF888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char c, int keyCode) {
        if (keyCode == 1) {
            closeSettings();
            return;
        }
        super.keyTyped(c, keyCode);
    }
}
