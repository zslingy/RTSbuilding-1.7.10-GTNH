package com.rtsbuilding.rtsbuilding.network.craft;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsJeiTransferMessage implements IMessage {

    private String recipeId;
    private boolean maxTransfer;
    private boolean clearGridFirst;

    public C2SRtsJeiTransferMessage() {}

    public C2SRtsJeiTransferMessage(String recipeId, boolean maxTransfer, boolean clearGridFirst) {
        this.recipeId = recipeId;
        this.maxTransfer = maxTransfer;
        this.clearGridFirst = clearGridFirst;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (recipeId != null) {
            byte[] bytes = recipeId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        } else {
            buf.writeInt(0);
        }
        buf.writeBoolean(maxTransfer);
        buf.writeBoolean(clearGridFirst);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        if (len > 0) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            recipeId = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            recipeId = "";
        }
        maxTransfer = buf.readBoolean();
        clearGridFirst = buf.readBoolean();
    }

    public String getRecipeId() {
        return recipeId;
    }

    public boolean isMaxTransfer() {
        return maxTransfer;
    }

    public boolean isClearGridFirst() {
        return clearGridFirst;
    }

    public static class Handler implements IMessageHandler<C2SRtsJeiTransferMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsJeiTransferMessage message, MessageContext ctx) {
            return null;
        }
    }
}
