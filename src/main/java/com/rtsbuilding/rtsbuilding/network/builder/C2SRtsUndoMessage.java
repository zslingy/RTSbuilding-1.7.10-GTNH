package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsUndoMessage implements IMessage {

    private boolean redo;
    private int count;

    public C2SRtsUndoMessage() {}

    public C2SRtsUndoMessage(boolean redo, int count) {
        this.redo = redo;
        this.count = count;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(redo);
        buf.writeInt(count);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        redo = buf.readBoolean();
        count = buf.readInt();
    }

    public static class Handler implements IMessageHandler<C2SRtsUndoMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUndoMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerHistoryManager mgr = ServerHistoryManager.getInstance();
            boolean success;
            if (m.redo) {
                success = mgr.executor()
                    .redo(player);
            } else {
                success = mgr.executor()
                    .undo(player);
            }

            return new S2CRtsHistorySyncMessage(success ? mgr.historyCount(player) : -1, mgr.redoCount(player));
        }
    }
}
