package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.MobBehavs.MobBloodlust;
import net.minecraft.world.entity.monster.Monster;

public class SuperSkeleton {
   public SuperSkeleton(Monster M) {
      apply(M);
   }

   public static void apply(Monster M) {
      MobBloodlust.apply(M);
   }
}
