package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class SnapToBlockCenter {
   public SnapToBlockCenter(Entity Entity_Class) {
      BlockPos Block_Pos = Entity_Class.blockPosition();
      int X = Block_Pos.getX();
      int Y = Block_Pos.getY();
      int Z = Block_Pos.getZ();
      double X_D = X;
      double Z_D = Z;
      Entity_Class.setPos(X_D, Y, Z_D);
   }
}
