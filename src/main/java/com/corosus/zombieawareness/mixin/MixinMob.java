package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MixinMob {

    @Inject(method = "finalizeSpawn",
            at = @At(value = "HEAD"), cancellable = true)
    public void hook(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, EntitySpawnReason pReason, SpawnGroupData pSpawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        ZombieAwareness.unitTest("8");
        Mob mob = (Mob)(Object)this;
        if (mob.level().isClientSide()) return;
        ZAUtil.processMobSpawn(mob);
        try {
            com.nll.nightmareesm.nightmareesm.onFinalizeSpawn(
                mob,
                pReason,
                com.corosus.zombieawareness.zday.ZDayProgressionManager.getCurrentStage().getStageNumber(),
                com.corosus.zombieawareness.bloodmoon.BloodmoonManager.isBloodmoonActive(mob.level())
            );
        } catch (Throwable ignored) {}
    }

    @Inject(method = "setTarget",
            at = @At(value = "HEAD"), cancellable = true)
    public void hook2(LivingEntity pTarget, CallbackInfo ci) {
        Mob ent = (Mob)(Object)this;
        if (pTarget != null && ZAUtil.shouldBlockHostileFriendlyFireOrAggro(ent, pTarget)) {
            ci.cancel();
            return;
        }
        ZAUtil.hookSetAttackTarget(ent, pTarget);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void hookTick(CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        if (!mob.level().isClientSide() && !mob.hasCustomName()) {
            try {
                com.nll.nightmareesm.nightmareesm.LivingMobTick(
                    mob,
                    com.corosus.zombieawareness.zday.ZDayProgressionManager.getCurrentStage().getStageNumber(),
                    com.corosus.zombieawareness.bloodmoon.BloodmoonManager.isBloodmoonActive(mob.level())
                );
            } catch (Throwable ignored) {}
        }
    }
}
