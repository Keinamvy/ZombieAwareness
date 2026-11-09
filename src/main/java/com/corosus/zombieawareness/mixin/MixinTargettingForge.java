package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StartAttacking.class)
public abstract class MixinTargettingForge {

    @Inject(method = "lambda$create$3",
            at = @At(value = "HEAD"))
    private static void hook(StartAttacking.StartAttackingCondition canAttack, StartAttacking.TargetFinder targetFinder, MemoryAccessor memoryAccessor, MemoryAccessor memoryAccessor2, ServerLevel serverLevel, Mob mob, long l, CallbackInfoReturnable<Boolean> cir) {
        Optional<? extends LivingEntity> optional = targetFinder.get(serverLevel, mob);
        if (optional.isPresent()) {
            LivingEntity livingEntity = optional.get();
            ZAUtil.test(mob, livingEntity);
            if (mob.canAttack(livingEntity)) {
                ZAUtil.hookSetAttackTarget(mob, livingEntity);
            }
        }
    }
}
