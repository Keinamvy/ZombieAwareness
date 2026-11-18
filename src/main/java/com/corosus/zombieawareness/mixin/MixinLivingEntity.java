package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Unique
    private long za$clutchInvulUntilTick = -1L;

    @Unique
    private boolean za$adrenalineKillHandled = false;

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
        if (source.is(DamageTypes.FALL) && victim.entityTags().contains(ZAUtil.THROWN_PROJECTILE_TAG)) {
            victim.removeTag(ZAUtil.THROWN_PROJECTILE_TAG);
            victim.addTag(ZAUtil.THROWN_LANDED_TAG);
            cir.setReturnValue(false);
            return;
        }

        if (victim instanceof ServerPlayer serverPlayer) {
            long gameTime = level.getGameTime();
            if (!ZAUtil.isClutchBypassDamage(source) && za$clutchInvulUntilTick > gameTime) {
                cir.setReturnValue(false);
                return;
            }
            if (ZAUtil.tryTriggerOneHeartClutch(serverPlayer, amount, source, gameTime)) {
                za$clutchInvulUntilTick = gameTime + ZAUtil.ONE_HEART_CLUTCH_INVUL_TICKS;
                cir.setReturnValue(false);
                return;
            }
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        if (victim instanceof Player player) {
            if ((attacker != null && attacker.entityTags().contains("za_crawler"))
                    || (directEntity != null && directEntity.entityTags().contains("za_crawler"))) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            }

            boolean isApex = (attacker != null && attacker.entityTags().contains("za_apex_corrosive"))
                    || (directEntity != null && directEntity.entityTags().contains("za_apex_corrosive"));
            if (isApex) {
                // 1. Necrotic Trauma: reduced regeneration efficiency
                ZAUtil.applyNecroticTrauma(player.getUUID(), level.getGameTime());

                // 2. Chance (50%) to Deal Direct Armor Durability Shredding
                if (level.getRandom().nextFloat() < 0.50f) {
                    ZAUtil.shredPlayerArmorDurability(player, level);
                }
            }
        }

        boolean isExplosion = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean bypassExplosionBlock = isExplosion && com.corosus.zombieawareness.config.ZAConfigZDay.allowHordeFriendlyExplosiveFire;
        if (!bypassExplosionBlock && (((attacker instanceof Creeper || directEntity instanceof Creeper) && ZAUtil.isHostileMob(victim))
                || (ZAUtil.isHostileMob(victim) && directEntity instanceof PrimedTnt tnt && (tnt.entityTags().contains("za_sapper_tnt") || ZAUtil.isHostileMob(tnt.getOwner())))
                || (ZAUtil.isHostileMob(victim) && attacker instanceof PrimedTnt attackerTnt && (attackerTnt.entityTags().contains("za_sapper_tnt") || ZAUtil.isHostileMob(attackerTnt.getOwner()))))
                || ZAUtil.shouldBlockHostileFriendlyFireOrAggro(attacker, victim, isExplosion)
                || ZAUtil.shouldBlockHostileFriendlyFireOrAggro(directEntity, victim, isExplosion)) {
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

    @Inject(method = "hurtServer", at = @At("RETURN"))
    public void za$onHurtServerReturnSpawnClones(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            LivingEntity victim = (LivingEntity)(Object)this;
            if (victim instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
                ZAUtil.trySpawnDamageClones(level, mob, source, amount);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    public void za$onEntityDie(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide() && self.level() instanceof ServerLevel serverLevel) {
            if (self instanceof ServerPlayer sp) {
                za$clutchInvulUntilTick = -1L;
                com.corosus.zombieawareness.heat.ChunkHeatManager.onPlayerDeath(serverLevel, sp.getUUID(), sp.blockPosition(), serverLevel.getGameTime());
            }
            if (!za$adrenalineKillHandled && !self.isRemoved()) {
                za$adrenalineKillHandled = true;
                ZAUtil.tryApplyAdrenalineLifeOnKill(serverLevel, self, source);
            }
            if (ZAUtil.isHostileMob(self)) {
                ZAUtil.trySpawnMonsterDistressScent(serverLevel, self, com.corosus.zombieawareness.zday.ZDayProgressionManager.getCurrentStage().getStageNumber());
            }
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void za$reduceHordeMobLootDrops(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        boolean isElite = ZAUtil.isEliteHordeMob(self);
        boolean isHordeMob = self.entityTags().contains("za_horde_mob") || ZAUtil.isHordeMob(self);
        boolean isDamageClone = self.entityTags().contains(ZAUtil.DAMAGE_CLONE_TAG);
        if (isElite) {
            ZAUtil.dropEliteMineralLoot(level, self, damageSource);
        } else if (isHordeMob) {
            ZAUtil.dropNormalHordeGunpowder(level, self);
        }
        if (!ZAUtil.shouldHordeMobDropLoot(isHordeMob, isElite, isDamageClone, level.getRandom().nextFloat())) {
            ci.cancel();
        }
    }

    @Inject(method = "setLastHurtByMob", at = @At("HEAD"), cancellable = true)
    public void hookSetLastHurtByMob(LivingEntity livingEntity, CallbackInfo ci) {
        if (ZAUtil.shouldBlockHostileFriendlyFireOrAggro(livingEntity, (Entity)(Object)this)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float za$modifyPlayerHealUnderNecroticTrauma(float healAmount) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide() && self instanceof Player player) {
            return ZAUtil.computeModifiedHealAmount(player.getUUID(), self.level().getGameTime(), healAmount);
        }
        return healAmount;
    }
}
