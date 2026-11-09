package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GiveRandomArmor;
import com.nll.nightmareesm.GiveRandomPickaxe;
import com.nll.nightmareesm.RNG;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NightmareZombie {
   private static final Logger LOGGER = LogManager.getLogger();

   public NightmareZombie(Entity Entity_Class) {
      this(Entity_Class, true);
   }

   public NightmareZombie(Entity Entity_Class, boolean allowPickaxe) {
      Monster M = (Monster)Entity_Class;
      if (allowPickaxe && (Boolean)ESMConfig.GiveZombiesPickaxe.get()) {
         int PickaxeWeightVal = new RNG().GetInt(0, 100);
         if (PickaxeWeightVal < (Integer)ESMConfig.ChanceOfPickaxe.get()) {
            new GiveRandomPickaxe(M);
         }
      }

      if ((Boolean)ESMConfig.GiveZombiesArmor.get()) {
         new GiveRandomArmor(M);
      }
   }
}
