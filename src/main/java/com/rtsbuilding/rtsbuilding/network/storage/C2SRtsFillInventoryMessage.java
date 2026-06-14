package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsFillInventoryMessage implements IMessage {

    private int windowId, buttonId;

    public C2SRtsFillInventoryMessage() {}

    public C2SRtsFillInventoryMessage(int w, int b) {
        windowId = w;
        buttonId = b;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(buttonId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        buttonId = buf.readInt();
    }

    public int getWindowId() {
        return windowId;
    }

    public int getButtonId() {
        return buttonId;
    }

    public static class Handler implements IMessageHandler<C2SRtsFillInventoryMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsFillInventoryMessage m, MessageContext c) {
            return null;
        }
    }
}
