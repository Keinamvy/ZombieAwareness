package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GiveRandomArmor;
import com.nll.nightmareesm.GiveRandomPickaxe;
import com.nll.nightmareesm.RNG;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

public class NightmareZombie {
   public NightmareZombie(Entity Entity_Class) {
      apply(Entity_Class, true);
   }

   public NightmareZombie(Entity Entity_Class, boolean allowPickaxe) {
      apply(Entity_Class, allowPickaxe);
   }

   public static void apply(Entity Entity_Class, boolean allowPickaxe) {
      if (!(Entity_Class instanceof Monster M)) {
         return;
      }
      if (allowPickaxe && (Boolean)ESMConfig.GiveZombiesPickaxe.get()) {
         int PickaxeWeightVal = RNG.getInt(0, 100);
         if (PickaxeWeightVal < (Integer)ESMConfig.ChanceOfPickaxe.get()) {
            GiveRandomPickaxe.give(M);
         }
      }

      if ((Boolean)ESMConfig.GiveZombiesArmor.get()) {
         GiveRandomArmor.give(M, false);
      }
   }
}
