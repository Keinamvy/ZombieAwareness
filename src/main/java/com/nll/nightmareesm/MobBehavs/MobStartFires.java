package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.RNG;
import com.nll.nightmareesm.helpers.ConfigHelper;
import com.corosus.zombieawareness.mixin.FireBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MobStartFires {
   public MobStartFires(Mob M) {
      tryStartFire(M);
   }

   public static void tryStartFire(Mob M) {
      if (M == null) {
         return;
      }
      if (RNG.getDouble(0.0, 100.0) >= (Double)ESMConfig.ChanceOfZombieLightFire.get()) {
         return;
      }
      LivingEntity Living_Entity = M.getTarget();
      if (!(Living_Entity instanceof ServerPlayer Server_Player) || !MobInfo.isSameDimension(M, Server_Player)) {
         return;
      }
      Level Lvl = MobInfo.GetMobLevel(M);
      if (Lvl == null || Lvl.isClientSide()) {
         return;
      }

      BlockPos center = M.blockPosition();
      BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
      BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

      for (int x = -1; x <= 1; x++) {
         for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
               mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
               if (!Lvl.isLoaded(mutablePos)) {
                  continue;
               }
               BlockState currState = Lvl.getBlockState(mutablePos);
               if (currState.isAir()) {
                  continue;
               }
               Block B = currState.getBlock();
               if (!isNotExcluded(B)) {
                  continue;
               }
               if (!areNeighborsLoaded(Lvl, mutablePos, neighborPos)) {
                  continue;
               }
               boolean canBurn = ((FireBlockAccessor)Blocks.FIRE).invokeCanBurn(currState)
                     || BaseFireBlock.canBePlacedAt(Lvl, mutablePos, Direction.NORTH);
               if (canBurn) {
                  lightFire(Lvl, mutablePos.immutable());
                  return;
               }
            }
         }
      }
   }

   private static boolean areNeighborsLoaded(Level lvl, BlockPos pos, BlockPos.MutableBlockPos scratch) {
      for (Direction dir : Direction.values()) {
         scratch.setWithOffset(pos, dir);
         if (!lvl.isLoaded(scratch)) {
            return false;
         }
      }
      return true;
   }

   private static boolean isNotExcluded(Block B) {
      return ConfigHelper.ConfigHasValue(ESMConfig.FlammableBlocksWhitelist, B, true);
   }

   private static void lightFire(Level Lvl, BlockPos Fire_Pos) {
      if (!MobInfo.isServerPosLoaded(Lvl, Fire_Pos)) {
         return;
      }
      Lvl.playLocalSound(Fire_Pos.getX(), Fire_Pos.getY(), Fire_Pos.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
      BlockState Block_State_Fire = BaseFireBlock.getState(Lvl, Fire_Pos);
      Lvl.setBlock(Fire_Pos, Block_State_Fire, 3);
   }
}
