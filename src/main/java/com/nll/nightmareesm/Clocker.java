package com.nll.nightmareesm;

import net.minecraft.world.entity.Entity;

public class Clocker {
   public static int GetIndexFromTime(int IndexCount) {
      if (IndexCount <= 0) {
         return 0;
      }
      long Index_Long_Val = Math.abs(System.currentTimeMillis()) % IndexCount;
      return (int)Index_Long_Val;
   }

   public static boolean IsAtTimeInterval(int TimeIntervalMS) {
      if (TimeIntervalMS <= 0) {
         return true;
      }
      return System.currentTimeMillis() % TimeIntervalMS == 0L;
   }

   public static boolean IsAtTimeInterval(Entity Entity_Class, int TimeIntervalMS) {
      if (TimeIntervalMS <= 0) {
         return true;
      }
      return Entity_Class.tickCount % TimeIntervalMS == 0;
   }
}
