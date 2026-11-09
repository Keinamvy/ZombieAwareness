package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class MobDigUp {
   float DigDist = 16.0F;

   public MobDigUp(Mob M) {
      Entity M_Entity = M;
      if (!M.isSwinging()) {
         BlockPos Zombie_Pos = M_Entity.blockPosition();
         Entity Tgt = M.getTarget();
         if (Tgt != null && Tgt instanceof ServerPlayer && this.isCloseEnough(M, Tgt) && this.isTargetAboveMonster(M, Tgt)) {
            BlockPos OverBlockPosHead = Zombie_Pos.above();
            Level Lvl = MobInfo.GetMobLevel(M);
            Block Dmg_Block_Head = Lvl.getBlockState(OverBlockPosHead).getBlock();
            new MobDamageBlock(M_Entity, Dmg_Block_Head, OverBlockPosHead);
            BlockPos OverBlockPosHead_2 = Zombie_Pos.above(2);
            Block Dmg_Block = Lvl.getBlockState(OverBlockPosHead_2).getBlock();
            new MobDamageBlock(M_Entity, Dmg_Block, OverBlockPosHead_2);
            M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
         }
      }
   }

   boolean isCloseEnough(Mob M, Entity Tgt) {
      double M_X = M.blockPosition().getX();
      double M_Z = M.blockPosition().getZ();
      double Tgt_X = Tgt.blockPosition().getX();
      double Tgt_Z = Tgt.blockPosition().getZ();
      return Math.abs(M_X - Tgt_X) < this.DigDist && Math.abs(M_Z - Tgt_Z) < this.DigDist;
   }

   boolean isTargetAboveMonster(Mob M, Entity Tgt) {
      double M_Y = M.blockPosition().getY();
      double TgtY = Tgt.blockPosition().getY();
      return TgtY > M_Y;
   }
}
