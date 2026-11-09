package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.GetBlockingBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.component.SwingAnimation;

public class MobDig {
   float DigDist = 2.0F;

   public MobDig(Entity Entity_Class) {
      Monster M = (Monster)Entity_Class;
      if (!M.isSwinging()) {
         LivingEntity Tgt = M.getTarget();
         boolean targetIsPlayer = Tgt instanceof ServerPlayer Server_Player && !Server_Player.isCreative();
         boolean navigatingToSense = Tgt == null && M.getNavigation() != null && M.getNavigation().getTargetPos() != null;
         if (targetIsPlayer || navigatingToSense) {
            GetBlockingBlock Blocking_Block = new GetBlockingBlock(Entity_Class);
            boolean isFaceBlocked = Blocking_Block.Current_Block == null || !Blocking_Block.Current_Block.defaultBlockState().isAir();
            boolean isFeetBlocked = Blocking_Block.Feet_Block == null || !Blocking_Block.Feet_Block.defaultBlockState().isAir();
            if (isFaceBlocked) {
               M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
               new MobDamageBlock(Entity_Class, Blocking_Block.Current_Block, Blocking_Block.Current_Position);
            }

            if (isFeetBlocked) {
               new MobDamageBlock(Entity_Class, Blocking_Block.Feet_Block, Blocking_Block.Feet_Position);
            }
         }
      }
   }

   boolean isAtLevel(Monster M, Entity Tgt) {
      double M_Y = M.blockPosition().getY();
      double Tgt_Y = Tgt.blockPosition().getY();
      double Dist = M_Y - Tgt_Y;
      Dist = Math.abs(Dist);
      return Dist < this.DigDist;
   }
}
