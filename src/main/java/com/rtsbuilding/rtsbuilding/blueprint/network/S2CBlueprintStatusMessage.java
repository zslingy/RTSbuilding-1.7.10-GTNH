package com.rtsbuilding.rtsbuilding.blueprint.network;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CBlueprintStatusMessage implements IMessage {

    public static final byte ERROR = 0;
    public static final byte INFO = 1;
    public static final byte SUCCESS = 2;

    private byte status;
    private String messageKey;
    private String detail;

    public S2CBlueprintStatusMessage() {}

    public S2CBlueprintStatusMessage(byte status, String messageKey, String detail) {
        this.status = status;
        this.messageKey = messageKey != null ? messageKey : "";
        this.detail = detail != null ? detail : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(status);
        ByteBufUtils.writeUTF8String(buf, messageKey);
        ByteBufUtils.writeUTF8String(buf, detail);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        status = buf.readByte();
        messageKey = ByteBufUtils.readUTF8String(buf);
        detail = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<S2CBlueprintStatusMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CBlueprintStatusMessage m, MessageContext c) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return null;

            String prefix;
            switch (m.status) {
                case ERROR:
                    prefix = EnumChatFormatting.RED + "[RTS Blueprint] ";
                    break;
                case SUCCESS:
                    prefix = EnumChatFormatting.GREEN + "[RTS Blueprint] ";
                    break;
                default:
                    prefix = EnumChatFormatting.YELLOW + "[RTS Blueprint] ";
                    break;
            }
            String text = prefix + m.messageKey + (m.detail.isEmpty() ? "" : ": " + m.detail);
            mc.thePlayer.addChatMessage(new ChatComponentText(text));
            return null;
        }
    }
}
