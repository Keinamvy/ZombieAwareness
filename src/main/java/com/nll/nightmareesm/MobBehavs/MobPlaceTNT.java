package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetNearestTarget;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.RNG;
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

   public MobPlaceTNT(Entity Entity_Class) {
      Entity Nearest_Entity = new GetNearestTarget().Get(Entity_Class);
      if (Nearest_Entity != null && this.CanPlaceThisTick() && this.isCloseEnough(Nearest_Entity, Entity_Class)) {
         BlockPos Entity_Block_Pos = Entity_Class.blockPosition();
         Mob Mob_Entity = (Mob)Entity_Class;
         ServerLevel Lvl = MobInfo.GetMobServerLevel(Mob_Entity);
         if (Lvl != null) {
            PrimedTnt New_TNT = new PrimedTnt(Lvl, Entity_Block_Pos.getX() + 0.5, Entity_Block_Pos.getY(), Entity_Block_Pos.getZ() + 0.5, null);
            Lvl.addFreshEntity(New_TNT);
            Lvl.playSound(null, Entity_Block_Pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
         }
      }
   }

   boolean isCloseEnough(Entity Target_Entity, Entity Entity_Class) {
      return Target_Entity.distanceTo(Entity_Class) <= ((Integer)ESMConfig.ZombieTNTDistance.get()).intValue();
   }

   boolean CanPlaceThisTick() {
      double Rand_Num = new RNG().GetDouble(0.0, 100.0);
      return Rand_Num <= (Double)ESMConfig.ZombieTNTChance.get();
   }
}
