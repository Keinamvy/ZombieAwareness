package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.RNG;
import com.corosus.zombieawareness.mixin.CreeperAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

public class NightmareCreeper {
   public NightmareCreeper(Entity Entity_Class, double ChargedChance) {
      apply(Entity_Class, ChargedChance);
   }

   public static void apply(Entity Entity_Class, double ChargedChance) {
      if (Entity_Class instanceof Creeper C && RNG.getDouble(0.0, 100.0) < ChargedChance) {
         C.getEntityData().set(CreeperAccessor.getDataIsPowered(), true);
      }
   }
}
