package com.nll.nightmareesm;

public class Clocker {
   public static int GetIndexFromTime(int IndexCount) {
      if (IndexCount <= 0) {
         return 0;
      }
      long Index_Long_Val = Math.abs(System.currentTimeMillis()) % IndexCount;
      return (int)Index_Long_Val;
   }
}
