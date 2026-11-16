package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.collision.HordeCollisionPolicy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntityCollision {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void zaho$suppressHordeEntityPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (HordeCollisionPolicy.shouldSuppressEntityPush(self, other)) {
            ci.cancel();
        }
    }
}
