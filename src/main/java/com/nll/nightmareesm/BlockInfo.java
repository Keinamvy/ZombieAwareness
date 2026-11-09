package com.nll.nightmareesm;

import net.minecraft.world.level.block.Block;

public class BlockInfo {
   public static boolean isLiquid(Block B) {
      return B.defaultBlockState().liquid();
   }

   public static boolean isAir(Block B) {
      return B.defaultBlockState().isAir();
   }
}
