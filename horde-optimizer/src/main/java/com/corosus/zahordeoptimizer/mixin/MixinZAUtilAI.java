package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.cluster.HordeClusterService;
import com.corosus.zombieawareness.ZAUtil;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ZAUtil.class, remap = false)
public abstract class MixinZAUtilAI {

    @Inject(method = "tickAI(Lnet/minecraft/world/entity/Mob;)V", at = @At("HEAD"), cancellable = true)
    private static void zaho$throttleZombieAwarenessAI(Mob ent, CallbackInfo ci) {
        if (HordeClusterService.shouldThrottleZombieAwarenessAI(ent)) {
            ci.cancel();
        }
    }
}
