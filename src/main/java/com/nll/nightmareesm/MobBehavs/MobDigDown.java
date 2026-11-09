package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class MobDigDown {
   public MobDigDown(Mob M) {
      Entity M_Entity = M;
      if (!M.isSwinging()) {
         BlockPos Zombie_Pos = M_Entity.blockPosition();
         Entity Tgt = M.getTarget();
         if (Tgt != null && Tgt instanceof ServerPlayer && this.isTargetUnderMonster(M, Tgt) && this.isCloseEnough(M, Tgt)) {
            BlockPos Under_BlockPos = Zombie_Pos.below();
            Level Lvl = MobInfo.GetMobLevel(M);
            Block Dmg_Block = Lvl.getBlockState(Under_BlockPos).getBlock();
            new MobDamageBlock(M_Entity, Dmg_Block, Under_BlockPos);
         }
      }
   }

   boolean isCloseEnough(Mob M, Entity Tgt) {
      double M_X = M.blockPosition().getX();
      double M_Z = M.blockPosition().getZ();
      double Tgt_X = Tgt.blockPosition().getX();
      double Tgt_Z = Tgt.blockPosition().getZ();
      Vec3 VecDistXZ = new Vec3(Math.abs(M_X - Tgt_X), 0.0, Math.abs(M_Z - Tgt_Z));
      double ActualXZDistToTgt = VecDistXZ.distanceTo(Tgt.position());
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
