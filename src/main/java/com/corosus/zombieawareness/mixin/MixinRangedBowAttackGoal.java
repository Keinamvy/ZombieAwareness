package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RangedBowAttackGoal.class)
public abstract class MixinRangedBowAttackGoal<T extends Monster & RangedAttackMob> {

    @Shadow @Final private T mob;
    @Shadow private int attackIntervalMin;
    @Shadow private int attackTime;

    @Inject(method = "tick", at = @At("HEAD"))
    private void za$accelerateRampageFireRate(CallbackInfo ci) {
        if (this.mob == null || !ChunkHeatManager.isRampageMob(this.mob)) {
            return;
        }
        this.attackIntervalMin = ChunkHeatManager.computeRampageBowInterval(this.attackIntervalMin);
        if (this.attackTime > 1) {
            this.attackTime--;
        }
        if (this.mob.isUsingItem() && this.mob.getTicksUsingItem() >= 10) {
            LivingEntity target = this.mob.getTarget();
            if (target != null && target.isAlive() && this.mob.getSensing().hasLineOfSight(target)) {
                this.mob.stopUsingItem();
                this.mob.performRangedAttack(target, 1.0F);
                this.attackTime = this.attackIntervalMin;
            }
        }
    }
}
