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
   private static final float DIG_DIST = 16.0F;

   public MobDigUp(Mob M) {
      tryDigUp(M);
   }

   public static void tryDigUp(Mob M) {
      if (M == null || M.isSwinging()) {
         return;
      }
      BlockPos Zombie_Pos = M.blockPosition();
      Entity Tgt = M.getTarget();
      if (Tgt instanceof ServerPlayer Server_Player && MobInfo.isSameDimension(M, Server_Player)
            && !Server_Player.isCreative() && !Server_Player.isSpectator()
            && isCloseEnough(M, Tgt) && isTargetAboveMonster(M, Tgt)) {
         Level Lvl = MobInfo.GetMobLevel(M);
         BlockPos OverBlockPosHead = Zombie_Pos.above();
         BlockPos OverBlockPosHead_2 = Zombie_Pos.above(2);
         boolean didDig = false;
         if (MobInfo.isServerPosLoaded(Lvl, OverBlockPosHead)) {
            Block Dmg_Block_Head = Lvl.getBlockState(OverBlockPosHead).getBlock();
            MobDamageBlock.damageBlock(M, Dmg_Block_Head, OverBlockPosHead);
            didDig = true;
         }
         if (MobInfo.isServerPosLoaded(Lvl, OverBlockPosHead_2)) {
            Block Dmg_Block = Lvl.getBlockState(OverBlockPosHead_2).getBlock();
            MobDamageBlock.damageBlock(M, Dmg_Block, OverBlockPosHead_2);
            didDig = true;
         }
         if (didDig) {
            M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
         }
      }
   }

   private static boolean isCloseEnough(Mob M, Entity Tgt) {
      double M_X = M.blockPosition().getX();
      double M_Z = M.blockPosition().getZ();
      double Tgt_X = Tgt.blockPosition().getX();
      double Tgt_Z = Tgt.blockPosition().getZ();
      return Math.abs(M_X - Tgt_X) < DIG_DIST && Math.abs(M_Z - Tgt_Z) < DIG_DIST;
   }

   private static boolean isTargetAboveMonster(Mob M, Entity Tgt) {
      return Tgt.blockPosition().getY() > M.blockPosition().getY();
   }
}
