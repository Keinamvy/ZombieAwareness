package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
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
import net.minecraft.world.level.chunk.LevelChunk;

public class MobBreakLightSources {
   private static final long LIGHT_SCAN_INTERVAL_TICKS = 80L;

   private static final class LightBlocksHolder {
      private static final Set<Block> LIGHT_BLOCKS = Set.of(
         Blocks.TORCH,
         Blocks.WALL_TORCH,
         Blocks.SOUL_TORCH,
         Blocks.SOUL_WALL_TORCH,
         Blocks.LANTERN,
         Blocks.SOUL_LANTERN,
         Blocks.GLOWSTONE
      );
   }

   public static boolean canInspectLightBlock(boolean hasLevel, boolean isClientSide, boolean isLoaded) {
      return MobInfo.canAccessServerBlock(hasLevel, isClientSide, isLoaded);
   }

   public static boolean shouldScanLightThisTick(long gameTime, int entityId) {
      return Math.floorMod(gameTime + entityId, LIGHT_SCAN_INTERVAL_TICKS) == 0L;
   }

   public static boolean tryBreakNearbyLight(Mob mob) {
      if (mob == null) {
         return false;
      }
      Level level = mob.level();
      if (level == null || level.isClientSide()) {
         return false;
      }
      if (!shouldScanLightThisTick(level.getGameTime(), mob.getId())) {
         return false;
      }

      BlockPos center = mob.blockPosition();
      int minX = center.getX() - 2;
      int maxX = center.getX() + 2;
      int minY = center.getY() - 1;
      int maxY = center.getY() + 2;
      int minZ = center.getZ() - 2;
      int maxZ = center.getZ() + 2;

      int minCx = minX >> 4;
      int maxCx = maxX >> 4;
      int minCz = minZ >> 4;
      int maxCz = maxZ >> 4;

      var chunkSource = level.getChunkSource();
      BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

      for (int cx = minCx; cx <= maxCx; cx++) {
         int chunkStartX = cx << 4;
         int startX = Math.max(minX, chunkStartX);
         int endX = Math.min(maxX, chunkStartX + 15);
         for (int cz = minCz; cz <= maxCz; cz++) {
            LevelChunk chunk = chunkSource.getChunkNow(cx, cz);
            if (chunk == null) {
               continue;
            }
            int chunkStartZ = cz << 4;
            int startZ = Math.max(minZ, chunkStartZ);
            int endZ = Math.min(maxZ, chunkStartZ + 15);
            for (int x = startX; x <= endX; x++) {
               for (int y = minY; y <= maxY; y++) {
                  for (int z = startZ; z <= endZ; z++) {
                     mutablePos.set(x, y, z);
                     BlockState state = chunk.getBlockState(mutablePos);
                     if (LightBlocksHolder.LIGHT_BLOCKS.contains(state.getBlock())) {
                        BlockPos targetPos = mutablePos.immutable();
                        SoundType soundType = state.getSoundType();
                        level.destroyBlock(targetPos, true, mob);
                        level.playSound(null, targetPos, soundType.getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
                        mob.swing(mob.getUsedItemHand(), SwingAnimation.DEFAULT);
                        return true;
                     }
                  }
               }
            }
         }
      }
      return false;
   }
}
