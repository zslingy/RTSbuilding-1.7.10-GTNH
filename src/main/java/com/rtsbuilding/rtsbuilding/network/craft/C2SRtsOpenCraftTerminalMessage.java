package com.rtsbuilding.rtsbuilding.network.craft;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.menu.RtsGuiHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsOpenCraftTerminalMessage implements IMessage {

    public C2SRtsOpenCraftTerminalMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsOpenCraftTerminalMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsOpenCraftTerminalMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // Open the craft terminal GUI via IGuiHandler
            // This creates the Container on server and GuiContainer on client
            player.openGui(
                RtsbuildingMod.instance,
                RtsGuiHandler.CRAFT_TERMINAL_GUI_ID,
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);
            return null;
        }
    }
}
