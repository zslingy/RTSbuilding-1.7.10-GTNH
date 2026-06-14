package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPlaceFluidMessage implements IMessage {

    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;
    private boolean forcePlace;
    private String fluidId;
    private double rayOriginX, rayOriginY, rayOriginZ;
    private double rayDirX, rayDirY, rayDirZ;

    public C2SRtsPlaceFluidMessage() {}

    public C2SRtsPlaceFluidMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ, boolean forcePlace, String fluidId, double rayOriginX, double rayOriginY, double rayOriginZ,
        double rayDirX, double rayDirY, double rayDirZ) {
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.forcePlace = forcePlace;
        this.fluidId = fluidId != null ? fluidId : "";
        this.rayOriginX = rayOriginX;
        this.rayOriginY = rayOriginY;
        this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX;
        this.rayDirY = rayDirY;
        this.rayDirZ = rayDirZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(clickedX);
        buf.writeInt(clickedY);
        buf.writeInt(clickedZ);
        buf.writeByte(face);
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
        buf.writeBoolean(forcePlace);
        writeUtf(buf, fluidId, 128);
        buf.writeDouble(rayOriginX);
        buf.writeDouble(rayOriginY);
        buf.writeDouble(rayOriginZ);
        buf.writeDouble(rayDirX);
        buf.writeDouble(rayDirY);
        buf.writeDouble(rayDirZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        clickedX = buf.readInt();
        clickedY = buf.readInt();
        clickedZ = buf.readInt();
        face = buf.readByte();
        hitX = buf.readDouble();
        hitY = buf.readDouble();
        hitZ = buf.readDouble();
        forcePlace = buf.readBoolean();
        fluidId = readUtf(buf, 128);
        rayOriginX = buf.readDouble();
        rayOriginY = buf.readDouble();
        rayOriginZ = buf.readDouble();
        rayDirX = buf.readDouble();
        rayDirY = buf.readDouble();
        rayDirZ = buf.readDouble();
    }

    private static void writeUtf(ByteBuf b, String s, int max) {
        if (s == null) s = "";
        byte[] d = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int l = Math.min(d.length, max);
        b.writeInt(l);
        b.writeBytes(d, 0, l);
    }

    private static String readUtf(ByteBuf b, int max) {
        int l = Math.max(0, Math.min(b.readInt(), max));
        byte[] d = new byte[l];
        if (l > 0) b.readBytes(d);
        return new String(d, java.nio.charset.StandardCharsets.UTF_8);
    }

    public int getClickedX() {
        return clickedX;
    }

    public int getClickedY() {
        return clickedY;
    }

    public int getClickedZ() {
        return clickedZ;
    }

    public byte getFace() {
        return face;
    }

    public double getHitX() {
        return hitX;
    }

    public double getHitY() {
        return hitY;
    }

    public double getHitZ() {
        return hitZ;
    }

    public boolean isForcePlace() {
        return forcePlace;
    }

    public String getFluidId() {
        return fluidId;
    }

    public double getRayOriginX() {
        return rayOriginX;
    }

    public double getRayOriginY() {
        return rayOriginY;
    }

    public double getRayOriginZ() {
        return rayOriginZ;
    }

    public double getRayDirX() {
        return rayDirX;
    }

    public double getRayDirY() {
        return rayDirY;
    }

    public double getRayDirZ() {
        return rayDirZ;
    }

    public static class Handler implements IMessageHandler<C2SRtsPlaceFluidMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPlaceFluidMessage msg, MessageContext ctx) {
            return null;
        }
    }
}
