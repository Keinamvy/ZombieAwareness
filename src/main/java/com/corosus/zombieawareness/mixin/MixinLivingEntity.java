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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Unique
    private boolean za$processingApexBypass = false;

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

        if (victim instanceof Player player && !za$processingApexBypass) {
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

                // 3. Chance (60%) to Bypass 40% of Player Armor
                if (level.getRandom().nextFloat() < 0.60f && amount > 0.0f) {
                    float[] split = ZAUtil.splitApexArmorBypassDamage(amount);
                    za$processingApexBypass = true;
                    try {
                        float bypassDmg = split[1];
                        float absorption = player.getAbsorptionAmount();
                        if (absorption > 0.0f) {
                            if (absorption >= bypassDmg) {
                                player.setAbsorptionAmount(absorption - bypassDmg);
                                bypassDmg = 0.0f;
                            } else {
                                player.setAbsorptionAmount(0.0f);
                                bypassDmg -= absorption;
                            }
                        }
                        if (bypassDmg > 0.0f) {
                            player.setHealth(Math.max(0.0f, player.getHealth() - bypassDmg));
                        }
                        boolean result = ((LivingEntity)(Object)this).hurtServer(level, source, split[0]);
                        cir.setReturnValue(result);
                        return;
                    } finally {
                        za$processingApexBypass = false;
                    }
                }
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
        if (!self.level().isClientSide() && self.level() instanceof ServerLevel serverLevel) {
            if (self instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.corosus.zombieawareness.heat.ChunkHeatManager.onPlayerDeath(serverLevel, sp.getUUID(), sp.blockPosition(), serverLevel.getGameTime());
            }
            if (ZAUtil.isHostileMob(self)) {
                ZAUtil.trySpawnMonsterDistressScent(serverLevel, self, com.corosus.zombieawareness.zday.ZDayProgressionManager.getCurrentStage().getStageNumber());
            }
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void za$reduceHordeMobLootDrops(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        boolean isHordeMob = self.entityTags().contains("za_horde_mob");
        if (isHordeMob && !ZAUtil.shouldHordeMobDropLoot(true, level.getRandom().nextFloat())) {
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
