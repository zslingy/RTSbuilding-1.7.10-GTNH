package com.rtsbuilding.rtsbuilding.network.craft;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C message to open the craft terminal screen.
 * DEPRECATED: Craft terminal now opens via IGuiHandler (player.openGui()).
 * This message is kept for network registration compatibility but is no longer sent.
 */
public class S2CRtsOpenCraftTerminalMessage implements IMessage {

    public S2CRtsOpenCraftTerminalMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<S2CRtsOpenCraftTerminalMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsOpenCraftTerminalMessage msg, MessageContext ctx) {
            // No longer used — craft terminal opens via IGuiHandler
            return null;
        }
    }
}
