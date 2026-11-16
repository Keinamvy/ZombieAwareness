package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomStrollGoal.class)
public abstract class MixinPreventWandering {

    @Shadow
    protected PathfinderMob mob;

    @Inject(method = "canUse",
            at = @At(value = "HEAD"), cancellable = true)
    public void canUse(CallbackInfoReturnable<Boolean> cir) {
        ZombieAwareness.unitTest("13");
        if (mob == null || mob.level() == null || mob.level().isClientSide()) {
            return;
        }
        long lastActionTime = ZombieAwareness.getPersistentDataSafe(mob).getLongOr(ZAUtil.ZA_LAST_ACTION, 0L);
        if (lastActionTime > 0 && mob.level().getGameTime() - ZAConfigGeneral.tickCooldownBetweenPathfinds < lastActionTime) {
            cir.setReturnValue(false);
        }
    }
}
