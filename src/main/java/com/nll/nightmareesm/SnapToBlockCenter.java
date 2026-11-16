package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Siege Engineer Zombie Build Ability — Block Centering Helper.
 *
 * <p>Snaps an entity to the center ({@code x + 0.5, y, z + 0.5}) of its current block after
 * stepping onto a newly placed pillar block in {@link com.nll.nightmareesm.MobBehavs.MobBuildUp}.
 */
public class SnapToBlockCenter {
   public SnapToBlockCenter(Entity Entity_Class) {
      snap(Entity_Class);
   }

   public static void snap(Entity Entity_Class) {
      if (Entity_Class == null) {
         return;
      }
      BlockPos Block_Pos = Entity_Class.blockPosition();
      Entity_Class.setPos(Block_Pos.getX() + 0.5, Block_Pos.getY(), Block_Pos.getZ() + 0.5);
   }
}
