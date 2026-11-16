package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class MobDigDown {
   public MobDigDown(Mob M) {
      tryDigDown(M);
   }

   public static void tryDigDown(Mob M) {
      if (M == null || M.isSwinging()) {
         return;
      }
      BlockPos Zombie_Pos = M.blockPosition();
      Entity Tgt = M.getTarget();
      if (Tgt instanceof ServerPlayer Server_Player && MobInfo.isSameDimension(M, Server_Player)
            && !Server_Player.isCreative() && !Server_Player.isSpectator()
            && isTargetUnderMonster(M, Tgt) && isCloseEnough(M, Tgt)) {
         BlockPos Under_BlockPos = Zombie_Pos.below();
         Level Lvl = MobInfo.GetMobLevel(M);
         if (MobInfo.isServerPosLoaded(Lvl, Under_BlockPos)) {
            Block Dmg_Block = Lvl.getBlockState(Under_BlockPos).getBlock();
            MobDamageBlock.damageBlock(M, Dmg_Block, Under_BlockPos);
         }
      }
   }

   public static double computeHorizontalDistance(double x1, double z1, double x2, double z2) {
      return Math.hypot(x1 - x2, z1 - z2);
   }

   private static boolean isCloseEnough(Mob M, Entity Tgt) {
      double M_X = M.blockPosition().getX();
      double M_Z = M.blockPosition().getZ();
      double Tgt_X = Tgt.blockPosition().getX();
      double Tgt_Z = Tgt.blockPosition().getZ();
      double ActualXZDistToTgt = computeHorizontalDistance(M_X, M_Z, Tgt_X, Tgt_Z);
      if (ActualXZDistToTgt < (Double)ESMConfig.ZombieDigProximity.get()) {
         double M_Y = M.blockPosition().getY();
         double Tgt_Y = Tgt.blockPosition().getY();
         double HeightDiff = M_Y - Tgt_Y;
         return HeightDiff < (Double)ESMConfig.ZombieDigHeightDistance.get();
      }
      return false;
   }

   private static boolean isTargetUnderMonster(Mob M, Entity Tgt) {
      return Tgt.blockPosition().getY() < M.blockPosition().getY();
   }
}
