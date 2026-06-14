package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsSetAutoStoreMessage implements IMessage {

    private boolean enabled;

    public S2CRtsSetAutoStoreMessage() {}

    public S2CRtsSetAutoStoreMessage(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        enabled = buf.readBoolean();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class Handler implements IMessageHandler<S2CRtsSetAutoStoreMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsSetAutoStoreMessage msg, MessageContext ctx) {
            com.rtsbuilding.rtsbuilding.client.RtsClientState state = com.rtsbuilding.rtsbuilding.client.RtsClientState
                .get();
            state.interaction.autoStoreMinedDrops = msg.isEnabled();
            return null;
        }
    }
}
