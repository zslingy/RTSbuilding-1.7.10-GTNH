package com.rtsbuilding.rtsbuilding.entity;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class RtsCameraEntity extends EntityLivingBase {

    private UUID ownerUuid;

    public RtsCameraEntity(World world) {
        super(world);
        this.noClip = true;
        this.ignoreFrustumCheck = true;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {}

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {}

    // ---- EntityLivingBase 四个抽象方法实现（相机实体不需要装备/物品）----

    @Override
    public ItemStack getHeldItem() {
        return null;
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[0];
    }

    // ---- 业务方法 ----

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    /**
     * 跳过 EntityLivingBase 的生命值/药水效果等更新逻辑。
     * RtsCameraEntity 仅是视角锚点，不需要物理或生命值更新。
     */
    @Override
    public void onUpdate() {
        // 不调用 super.onUpdate() —— 跳过 EntityLivingBase 的所有副作用
        this.noClip = true;
        this.ignoreFrustumCheck = true;
    }

    /**
     * 防御性空实现（由于 onUpdate 不调用 super，本不会被触发）。
     */
    @Override
    public void onLivingUpdate() {
        // 空实现
    }

    public void snapTo(double x, double y, double z, float yaw, float pitch) {
        this.setPosition(x, y, z);
        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.prevRotationYaw = yaw;
        this.prevRotationPitch = pitch;
    }
}
