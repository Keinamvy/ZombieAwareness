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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobPlaceTNT {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final Map<UUID, Long> lastTntPlacedTick = new ConcurrentHashMap<>();

   public MobPlaceTNT(Entity Entity_Class) {
      if (Entity_Class == null) {
         return;
      }
      Mob Mob_Entity = (Mob)Entity_Class;
      ServerLevel Lvl = MobInfo.GetMobServerLevel(Mob_Entity);
      if (Lvl == null) {
         return;
      }

      long gameTime = Lvl.getGameTime();
      Long lastTick = lastTntPlacedTick.get(Entity_Class.getUUID());
      if (lastTick != null && gameTime - lastTick < 200L) {
         return;
      }

      Entity Nearest_Entity = new GetNearestTarget().Get(Entity_Class);
      if (Nearest_Entity != null && this.CanPlaceThisTick(Entity_Class) && this.isCloseEnough(Nearest_Entity, Entity_Class)) {
         lastTntPlacedTick.put(Entity_Class.getUUID(), gameTime);
         BlockPos Entity_Block_Pos = Entity_Class.blockPosition();
         PrimedTnt New_TNT = new PrimedTnt(Lvl, Entity_Block_Pos.getX() + 0.5, Entity_Block_Pos.getY(), Entity_Block_Pos.getZ() + 0.5, Mob_Entity);
         New_TNT.addTag("za_sapper_tnt");
         Lvl.addFreshEntity(New_TNT);
         Lvl.playSound(null, Entity_Block_Pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
   }

   boolean isCloseEnough(Entity Target_Entity, Entity Entity_Class) {
      if (Entity_Class != null && Entity_Class.entityTags().contains("za_tnt_sapper")) {
         return Target_Entity.distanceTo(Entity_Class) <= 4.0;
      }
      return Target_Entity.distanceTo(Entity_Class) <= ((Integer)ESMConfig.ZombieTNTDistance.get()).intValue();
   }

   boolean CanPlaceThisTick() {
      double Rand_Num = new RNG().GetDouble(0.0, 100.0);
      return Rand_Num <= (Double)ESMConfig.ZombieTNTChance.get();
   }

   boolean CanPlaceThisTick(Entity Entity_Class) {
      if (Entity_Class != null && Entity_Class.entityTags().contains("za_tnt_sapper")) {
         return new RNG().GetInt(0, 100) < 25;
      }
      return this.CanPlaceThisTick();
   }

   public static void resetCooldown(UUID uuid) {
      lastTntPlacedTick.remove(uuid);
   }

   public static void clearCooldowns() {
      lastTntPlacedTick.clear();
   }
}
