package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import net.minecraft.world.entity.Entity;

public class MobMaxLife {
   public MobMaxLife(Entity Entity_Class) {
      checkAndDiscard(Entity_Class);
   }

   public static void checkAndDiscard(Entity Entity_Class) {
      if (Entity_Class != null && !Entity_Class.hasCustomName() && Entity_Class.tickCount > (Integer)ESMConfig.MobMaxLife.get()) {
         Entity_Class.stopRiding();
         Entity_Class.discard();
      }
   }
}
