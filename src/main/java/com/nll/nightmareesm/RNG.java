package com.nll.nightmareesm;

import java.util.concurrent.ThreadLocalRandom;

public class RNG {
   public int GetInt(int Min, int Max) {
      if (Min >= Max) {
         return Min;
      }
      return ThreadLocalRandom.current().nextInt(Min, Max);
   }

   public double GetDouble(double Min, double Max) {
      if (Min >= Max) {
         return Min;
      }
      return ThreadLocalRandom.current().nextDouble(Min, Max);
   }
}
