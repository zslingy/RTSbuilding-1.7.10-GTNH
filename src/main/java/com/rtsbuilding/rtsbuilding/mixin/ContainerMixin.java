package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;

@Mixin(Container.class)
abstract class ContainerMixin {

    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$forceRemoteCanInteract(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (RtsRemoteMenuCompat.shouldForceStillValid((Container) (Object) this, player)) {
            cir.setReturnValue(true);
        }
    }
}
