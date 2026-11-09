package com.nll.nightmareesm.MobBehavs;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class MobBreakLightSources {
   private static final Set<Block> LIGHT_BLOCKS = Set.of(
      Blocks.TORCH,
      Blocks.WALL_TORCH,
      Blocks.SOUL_TORCH,
      Blocks.SOUL_WALL_TORCH,
      Blocks.LANTERN,
      Blocks.SOUL_LANTERN,
      Blocks.GLOWSTONE
   );

   public static boolean tryBreakNearbyLight(Mob mob) {
      if (mob == null) {
         return false;
      }
      Level level = mob.level();
      if (level == null || level.isClientSide()) {
         return false;
      }

      BlockPos center = mob.blockPosition();
      for (int x = -2; x <= 2; x++) {
         for (int y = -1; y <= 2; y++) {
            for (int z = -2; z <= 2; z++) {
               BlockPos targetPos = center.offset(x, y, z);
               BlockState state = level.getBlockState(targetPos);
               if (LIGHT_BLOCKS.contains(state.getBlock())) {
                  SoundType soundType = state.getSoundType();
                  level.destroyBlock(targetPos, true, mob);
                  level.playSound(null, targetPos, soundType.getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
                  mob.swing(mob.getUsedItemHand(), SwingAnimation.DEFAULT);
                  return true;
               }
            }
         }
      }
      return false;
   }
}
