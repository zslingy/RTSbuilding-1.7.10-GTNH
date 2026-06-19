package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CRtsHistorySyncMessage implements IMessage {

    private int historyCount;
    private int redoCount;

    public S2CRtsHistorySyncMessage() {}

    public S2CRtsHistorySyncMessage(int historyCount, int redoCount) {
        this.historyCount = historyCount;
        this.redoCount = redoCount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(historyCount);
        buf.writeInt(redoCount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        historyCount = buf.readInt();
        redoCount = buf.readInt();
    }

    public int getHistoryCount() {
        return historyCount;
    }

    public int getRedoCount() {
        return redoCount;
    }

    public static class Handler implements IMessageHandler<S2CRtsHistorySyncMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsHistorySyncMessage m, MessageContext ctx) {
            RtsClientState.get()
                .setHistoryCounts(m.historyCount, m.redoCount);
            return null;
        }
    }
}
