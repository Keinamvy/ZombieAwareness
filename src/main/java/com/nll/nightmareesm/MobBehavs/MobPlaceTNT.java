package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetNearestTarget;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.RNG;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;

public class MobPlaceTNT {
   private static final Map<UUID, Long> lastTntPlacedTick = new ConcurrentHashMap<>();
   private static final long TNT_COOLDOWN_TICKS = 200L;
   private static final int MAX_COOLDOWN_ENTRIES = 512;
   private static volatile long lastPruneTick = -1L;

   public MobPlaceTNT(Entity Entity_Class) {
      tryPlace(Entity_Class);
   }

   public static void tryPlace(Entity Entity_Class) {
      if (!(Entity_Class instanceof Mob Mob_Entity)) {
         return;
      }
      ServerLevel Lvl = MobInfo.GetMobServerLevel(Mob_Entity);
      if (Lvl == null) {
         return;
      }

      long gameTime = Lvl.getGameTime();
      if (lastTntPlacedTick.size() > MAX_COOLDOWN_ENTRIES
            && (lastPruneTick < 0L || gameTime < lastPruneTick || (gameTime - lastPruneTick) >= TNT_COOLDOWN_TICKS)) {
         lastPruneTick = gameTime;
         lastTntPlacedTick.entrySet().removeIf(e -> gameTime < e.getValue() || (gameTime - e.getValue()) >= TNT_COOLDOWN_TICKS);
      }
      Long lastTick = lastTntPlacedTick.get(Entity_Class.getUUID());
      if (lastTick != null && gameTime >= lastTick && (gameTime - lastTick) < TNT_COOLDOWN_TICKS) {
         return;
      }

      if (!canPlaceThisTick(Entity_Class)) {
         return;
      }

      Entity Nearest_Entity = resolveTarget(Mob_Entity);
      if (Nearest_Entity != null && MobInfo.isSameDimension(Nearest_Entity, Entity_Class)
            && isCloseEnough(Nearest_Entity, Entity_Class)) {
         lastTntPlacedTick.put(Entity_Class.getUUID(), gameTime);
         BlockPos Entity_Block_Pos = Entity_Class.blockPosition();
         PrimedTnt New_TNT = new PrimedTnt(Lvl, Entity_Block_Pos.getX() + 0.5, Entity_Block_Pos.getY(), Entity_Block_Pos.getZ() + 0.5, Mob_Entity);
         New_TNT.addTag("za_sapper_tnt");
         Lvl.addFreshEntity(New_TNT);
         Lvl.playSound(null, Entity_Block_Pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
   }

   private static Entity resolveTarget(Mob mobEntity) {
      LivingEntity existingTarget = mobEntity.getTarget();
      if (existingTarget instanceof ServerPlayer sp
            && sp.isAlive()
            && MobInfo.isSameDimension(sp, mobEntity)
            && GetNearestTarget.isValidSurvivalPlayerTarget(sp.isCreative(), sp.isSpectator())) {
         return sp;
      }
      return GetNearestTarget.getNearest(mobEntity);
   }

   private static boolean isCloseEnough(Entity Target_Entity, Entity Entity_Class) {
      double distSq = Target_Entity.distanceToSqr(Entity_Class);
      if (Entity_Class != null && Entity_Class.entityTags().contains("za_tnt_sapper")) {
         return distSq <= 16.0;
      }
      int maxDist = (Integer)ESMConfig.ZombieTNTDistance.get();
      return distSq <= (double)(maxDist * maxDist);
   }

   private static boolean canPlaceThisTick(Entity Entity_Class) {
      if (Entity_Class != null && Entity_Class.entityTags().contains("za_tnt_sapper")) {
         return RNG.getInt(0, 100) < 25;
      }
      return RNG.getDouble(0.0, 100.0) <= (Double)ESMConfig.ZombieTNTChance.get();
   }

   public static void resetCooldown(UUID uuid) {
      lastTntPlacedTick.remove(uuid);
   }

   public static void clearCooldowns() {
      lastTntPlacedTick.clear();
      lastPruneTick = -1L;
   }
}
