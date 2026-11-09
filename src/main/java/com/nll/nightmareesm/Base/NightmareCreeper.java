package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.RNG;
import com.corosus.zombieawareness.mixin.CreeperAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NightmareCreeper {
   private static final Logger LOGGER = LogManager.getLogger();

   public NightmareCreeper(Entity Entity_Class, double ChargedChance) {
      double Creeper_RNG = new RNG().GetDouble(0.0, 100.0);
      if (Creeper_RNG < ChargedChance) {
         this.MakeCharged(Entity_Class);
      }
   }

   void MakeCharged(Entity Entity_Class) {
      Creeper C = (Creeper)Entity_Class;
      C.getEntityData().set(CreeperAccessor.getDataIsPowered(), true);
   }

   public static void CreeperTick(Creeper C) {
   }

   void KillIfOverXTicks() {
   }
}
