package com.nll.nightmareesm;

import java.util.Random;

public class RNG {
   public int GetInt(int Min, int Max) {
      if (Min >= Max) {
         return Min;
      }
      Random New_RNG = new Random();
      return New_RNG.nextInt(Min, Max);
   }

   public double GetDouble(double Min, double Max) {
      if (Min >= Max) {
         return Min;
      }
      Random New_RNG = new Random();
      return New_RNG.nextDouble(Min, Max);
   }
}
