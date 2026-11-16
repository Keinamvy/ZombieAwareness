package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.DuplicateMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class MobScreamerAlert {
   private static final Object SCREAM_LOCK = new Object();
   private static volatile long lastGlobalScreamTick = -999999L;

   public static boolean shouldThrottleScream(long gameTime, long lastScreamTick) {
      if (lastScreamTick < 0L || gameTime < lastScreamTick) {
         return false;
      }
      return (gameTime - lastScreamTick) < 100L;
   }

   public static boolean checkAndTrigger(Zombie zombie) {
      if (zombie == null) {
         return false;
      }
      if (zombie.entityTags().contains("za_screamer")
         && !zombie.entityTags().contains("za_screamer_triggered")
         && zombie.getTarget() != null
         && zombie.level() instanceof ServerLevel serverLevel) {

         long gameTime = serverLevel.getGameTime();
         synchronized (SCREAM_LOCK) {
            if (shouldThrottleScream(gameTime, lastGlobalScreamTick)) {
               return false;
            }
            lastGlobalScreamTick = gameTime;
         }

         zombie.addTag("za_screamer_triggered");
         serverLevel.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.5F, 1.0F);
         serverLevel.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0F, 1.0F);
         zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 1));
         DuplicateMob.duplicate(zombie, 3);
         return true;
      }
      return false;
   }

   public static boolean tickScreamer(Zombie zombie) {
      return checkAndTrigger(zombie);
   }

   public static void resetLastGlobalScreamTick() {
      synchronized (SCREAM_LOCK) {
         lastGlobalScreamTick = -999999L;
      }
   }

   public static long getLastGlobalScreamTick() {
      return lastGlobalScreamTick;
   }

   public static void setLastGlobalScreamTickForTest(long tick) {
      lastGlobalScreamTick = tick;
   }
}
