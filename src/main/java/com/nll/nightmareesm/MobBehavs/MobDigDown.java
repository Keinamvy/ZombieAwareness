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
      if (M == null) {
         return;
      }
      Entity M_Entity = M;
      if (!M.isSwinging()) {
         BlockPos Zombie_Pos = M_Entity.blockPosition();
         Entity Tgt = M.getTarget();
         if (Tgt instanceof ServerPlayer Server_Player && MobInfo.isSameDimension(M, Server_Player)
               && !Server_Player.isCreative() && !Server_Player.isSpectator()
               && this.isTargetUnderMonster(M, Tgt) && this.isCloseEnough(M, Tgt)) {
            BlockPos Under_BlockPos = Zombie_Pos.below();
            Level Lvl = MobInfo.GetMobLevel(M);
            if (MobInfo.isServerPosLoaded(Lvl, Under_BlockPos)) {
               Block Dmg_Block = Lvl.getBlockState(Under_BlockPos).getBlock();
               new MobDamageBlock(M_Entity, Dmg_Block, Under_BlockPos);
            }
         }
      }
   }

   public static double computeHorizontalDistance(double x1, double z1, double x2, double z2) {
      return Math.hypot(x1 - x2, z1 - z2);
   }

   boolean isCloseEnough(Mob M, Entity Tgt) {
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
      } else {
         return false;
      }
   }

   boolean isTargetUnderMonster(Mob M, Entity Tgt) {
      double M_Y = M.blockPosition().getY();
      double TgtY = Tgt.blockPosition().getY();
      return TgtY < M_Y;
   }
}
