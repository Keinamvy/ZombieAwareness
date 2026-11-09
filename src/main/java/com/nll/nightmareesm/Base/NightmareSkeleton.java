package com.nll.nightmareesm.Base;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GiveRandomArmor;
import com.nll.nightmareesm.RNG;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

public class NightmareSkeleton {
   public NightmareSkeleton(Entity Entity_Class) {
      Monster M = (Monster)Entity_Class;
      if ((Boolean)ESMConfig.GiveSkeletonsArmor.get()) {
         new GiveRandomArmor(M);
      }

      if ((Boolean)ESMConfig.AllowSuperSkeletons.get()) {
         int SkeleWeightValue = new RNG().GetInt(0, 100);
         double Chance_SuperSkeleton = (Double)ESMConfig.ChanceOfSuperSkeleton.get();
         if (SkeleWeightValue < Chance_SuperSkeleton) {
            new SuperSkeleton(M);
         }
      }
   }
}
