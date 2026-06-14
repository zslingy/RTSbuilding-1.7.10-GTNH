package com.rtsbuilding.rtsbuilding.server.menu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.client.screen.RtsCraftTerminalScreen;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * GUI handler for RTS Building mod.
 * Handles server-side Container creation and client-side GuiContainer creation.
 */
public class RtsGuiHandler implements IGuiHandler {

    public static final int CRAFT_TERMINAL_GUI_ID = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == CRAFT_TERMINAL_GUI_ID) {
            return new RtsCraftTerminalMenu(player.inventory);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == CRAFT_TERMINAL_GUI_ID) {
            return new RtsCraftTerminalScreen(new RtsCraftTerminalMenu(player.inventory));
        }
        return null;
    }
}
