package com.nll.nightmareesm.MobBehavs;

import net.minecraft.world.entity.monster.Creeper;

public class CreeperExplode {
   public CreeperExplode(Creeper C) {
      ignite(C);
   }

   public static void ignite(Creeper C) {
      if (C != null) {
         C.ignite();
      }
   }
}
