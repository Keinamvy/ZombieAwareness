package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.heat.HordeSpecialUnitHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
public abstract class MixinWarden {

    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void za$preventHordeWardenFriendlyTargets(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Warden self = (Warden) (Object) this;
        if (HordeSpecialUnitHelper.shouldBlockWardenTargeting(self, entity)) {
            cir.setReturnValue(false);
        }
    }
}
