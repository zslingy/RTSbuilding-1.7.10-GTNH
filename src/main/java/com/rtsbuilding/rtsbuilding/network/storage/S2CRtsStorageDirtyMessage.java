package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CRtsStorageDirtyMessage implements IMessage {

    private int storageId;
    private byte dirtyFlags;

    public S2CRtsStorageDirtyMessage() {}

    public S2CRtsStorageDirtyMessage(int storageId, byte dirtyFlags) {
        this.storageId = storageId;
        this.dirtyFlags = dirtyFlags;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(storageId);
        buf.writeByte(dirtyFlags);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        storageId = buf.readInt();
        dirtyFlags = buf.readByte();
    }

    public int getStorageId() {
        return storageId;
    }

    public byte getDirtyFlags() {
        return dirtyFlags;
    }

    public static class Handler implements IMessageHandler<S2CRtsStorageDirtyMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsStorageDirtyMessage m, MessageContext ctx) {
            RtsClientState.get().storage.markDirty(m.storageId, m.dirtyFlags);
            return null;
        }
    }
}
