package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobMaxLife {
   private static final Logger LOGGER = LogManager.getLogger();

   public MobMaxLife(Entity Entity_Class) {
      if (!Entity_Class.hasCustomName() && Entity_Class.tickCount > (Integer)ESMConfig.MobMaxLife.get()) {
         this.KillMobRiding(Entity_Class);
         Entity_Class.discard();
      }
   }

   void KillMobRiding(Entity Entity_Class) {
      Entity Veh = Entity_Class.getVehicle();
      if (Veh instanceof LivingEntity) {
         Veh.discard();
      }
   }
}
