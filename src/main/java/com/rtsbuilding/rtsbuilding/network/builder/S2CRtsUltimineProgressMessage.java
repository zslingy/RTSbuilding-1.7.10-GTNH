package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsUltimineProgressMessage implements IMessage {

    private int processed, total;

    public S2CRtsUltimineProgressMessage() {}

    public S2CRtsUltimineProgressMessage(int processed, int total) {
        this.processed = processed;
        this.total = total;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(processed);
        buf.writeInt(total);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        processed = buf.readInt();
        total = buf.readInt();
    }

    public int getProcessed() {
        return processed;
    }

    public int getTotal() {
        return total;
    }

    public static class Handler implements IMessageHandler<S2CRtsUltimineProgressMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsUltimineProgressMessage msg, MessageContext ctx) {
            com.rtsbuilding.rtsbuilding.client.InteractionViewModel ivm = com.rtsbuilding.rtsbuilding.client.RtsClientState
                .get().interaction;
            ivm.ultimineProgressProcessed = msg.getProcessed();
            ivm.ultimineProgressTotal = msg.getTotal();
            return null;
        }
    }
}
