package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Isolated Zombie Build Ability — Block Centering Helper.
 *
 * <p>Snaps an entity to block-aligned coordinates after stepping onto a newly placed pillar block
 * in {@link com.nll.nightmareesm.MobBehavs.MobBuildUp}.
 *
 * <p><b>Current Status:</b> Kept intact as a dependency of the isolated Zombie Build ability.
 */
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
