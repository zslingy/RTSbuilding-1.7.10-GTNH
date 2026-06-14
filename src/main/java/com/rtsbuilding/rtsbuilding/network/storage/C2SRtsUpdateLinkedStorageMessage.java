package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsUpdateLinkedStorageMessage implements IMessage {

    private int posX, posY, posZ;
    private byte action;

    public C2SRtsUpdateLinkedStorageMessage() {}

    public C2SRtsUpdateLinkedStorageMessage(int x, int y, int z, byte a) {
        posX = x;
        posY = y;
        posZ = z;
        action = a;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(action);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        action = buf.readByte();
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public byte getAction() {
        return action;
    }

    public static class Handler implements IMessageHandler<C2SRtsUpdateLinkedStorageMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUpdateLinkedStorageMessage m, MessageContext c) {
            return null;
        }
    }
}
