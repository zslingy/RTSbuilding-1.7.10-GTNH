package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.client.entity.EntityClientPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

@Mixin(EntityClientPlayerMP.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void rtsbuilding$freezeMovementInRtsMode(CallbackInfo ci) {
        if (RtsClientState.get().camera.isActive) {
            EntityClientPlayerMP self = (EntityClientPlayerMP) (Object) this;
            self.movementInput.moveForward = 0.0F;
            self.movementInput.moveStrafe = 0.0F;
            self.movementInput.jump = false;
        }
    }
}
