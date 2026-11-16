package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GiveRandomArmor;
import com.nll.nightmareesm.RNG;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

public class NightmareSkeleton {
   public NightmareSkeleton(Entity Entity_Class) {
      apply(Entity_Class);
   }

   public static void apply(Entity Entity_Class) {
      if (!(Entity_Class instanceof Monster M)) {
         return;
      }
      if ((Boolean)ESMConfig.GiveSkeletonsArmor.get()) {
         GiveRandomArmor.give(M, false);
      }

      if ((Boolean)ESMConfig.AllowSuperSkeletons.get()) {
         int SkeleWeightValue = RNG.getInt(0, 100);
         double Chance_SuperSkeleton = (Double)ESMConfig.ChanceOfSuperSkeleton.get();
         if (SkeleWeightValue < Chance_SuperSkeleton) {
            SuperSkeleton.apply(M);
         }
      }
   }
}
