package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "tick",
            at = @At(value = "HEAD"), cancellable = true)
    public void hook(CallbackInfo ci) {
        ZombieAwareness.unitTest("7");
        LivingEntity ent = ((LivingEntity)(Object)this);
        if (ent.level().isClientSide()) return;

        if (ent instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ZAUtil.checkPortalLockdown(serverPlayer);
        }

        if ((ent.level().getGameTime() + ent.getId()) % Math.max(1, ZAConfigGeneral.tickRateAILoop) == 0) {
            if (ZombieAwareness.canProcessEntity(ent) && ent instanceof Mob) {
                ZAUtil.tickAI((Mob) ent);
            }
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    public void za$blockHostileFriendlyFire(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity victim = (Entity)(Object)this;
        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        if (victim instanceof Player player) {
            if ((attacker != null && attacker.entityTags().contains("za_crawler"))
                    || (directEntity != null && directEntity.entityTags().contains("za_crawler"))) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            }
        }

        if (((attacker instanceof Creeper || directEntity instanceof Creeper) && ZAUtil.isHostileMob(victim))
                || (ZAUtil.isHostileMob(victim) && directEntity instanceof PrimedTnt tnt && (tnt.entityTags().contains("za_sapper_tnt") || ZAUtil.isHostileMob(tnt.getOwner())))
                || (ZAUtil.isHostileMob(victim) && attacker instanceof PrimedTnt attackerTnt && (attackerTnt.entityTags().contains("za_sapper_tnt") || ZAUtil.isHostileMob(attackerTnt.getOwner())))
                || ZAUtil.shouldBlockHostileFriendlyFireOrAggro(attacker, victim)
                || ZAUtil.shouldBlockHostileFriendlyFireOrAggro(directEntity, victim)) {
            cir.setReturnValue(false);
            return;
        }

        if (victim instanceof Mob mob && !level.isClientSide()) {
            try {
                com.nll.nightmareesm.nightmareesm.onMobDamaged(
                    mob,
                    amount,
                    com.corosus.zombieawareness.zday.ZDayProgressionManager.getCurrentStage().getStageNumber()
                );
            } catch (Throwable ignored) {}
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    public void za$onEntityDie(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide() && self instanceof net.minecraft.server.level.ServerPlayer sp && self.level() instanceof ServerLevel serverLevel) {
            com.corosus.zombieawareness.heat.ChunkHeatManager.onPlayerDeath(serverLevel, sp.getUUID(), sp.blockPosition(), serverLevel.getGameTime());
        }
    }

    @Inject(method = "setLastHurtByMob", at = @At("HEAD"), cancellable = true)
    public void hookSetLastHurtByMob(LivingEntity livingEntity, CallbackInfo ci) {
        if (ZAUtil.shouldBlockHostileFriendlyFireOrAggro(livingEntity, (Entity)(Object)this)) {
            ci.cancel();
        }
    }
}
