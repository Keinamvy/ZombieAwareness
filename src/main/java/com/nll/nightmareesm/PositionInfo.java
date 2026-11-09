package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;

public class PositionInfo {
   public static BlockPos OffsetPos(BlockPos Original_Pos, double X, double Y, double Z) {
      return Original_Pos.offset((int)X, (int)Y, (int)Z);
   }
}
