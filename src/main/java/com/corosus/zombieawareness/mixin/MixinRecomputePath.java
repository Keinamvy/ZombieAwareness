package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public abstract class MixinRecomputePath {

    @Shadow
    protected Mob mob;

    @Inject(method = "shouldRecomputePath",
            at = @At(value = "HEAD"), cancellable = true)
    public void shouldRecomputePath(BlockPos p_200904_, CallbackInfoReturnable<Boolean> cir) {
        ZombieAwareness.unitTest("14");
        if (mob == null || mob.level() == null || mob.level().isClientSide()) {
            return;
        }
        long lastActionTime = ZombieAwareness.getLastActionTick(mob);
        if (lastActionTime > 0 && mob.level().getGameTime() - ZAConfigGeneral.tickCooldownBetweenPathfinds < lastActionTime) {
            cir.setReturnValue(false);
        }
    }
}
