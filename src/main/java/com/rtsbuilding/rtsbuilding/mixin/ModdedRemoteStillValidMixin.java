package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "ironfurnaces.container.BlockIronFurnaceContainerBase", remap = false)
public abstract class ModdedRemoteStillValidMixin {

    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$forceRemoteStillValid(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
