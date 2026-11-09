package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class MobBuildBridge {
   Block Block_Type = Blocks.COBBLESTONE;
   public boolean isDoing;

   public MobBuildBridge(Mob M) {
      Entity Tgt = M.getTarget();
      if (Tgt != null && Tgt instanceof ServerPlayer && this.isMonsterAtTargetLvl(M, Tgt)) {
         BlockPos M_Pos = M.blockPosition();
         M.lookAt(Tgt, 0.0F, 0.0F);
         BlockPos Fwd_Block_Pos = M_Pos.relative(M.getDirection());
         Level Curr_Level = MobInfo.GetMobLevel(M);
         Block Current_Block = Curr_Level.getBlockState(Fwd_Block_Pos).getBlock();
         if (Current_Block.defaultBlockState().isAir()) {
            BlockPos Block_Below_Pos = Fwd_Block_Pos.below();
            BlockPos Block_Below_Pos2 = Block_Below_Pos.below();
            Block Block_Below = Curr_Level.getBlockState(Block_Below_Pos).getBlock();
            Block Block_Below2 = Curr_Level.getBlockState(Block_Below_Pos2).getBlock();
            if (Block_Below.defaultBlockState().isAir() && Block_Below2.defaultBlockState().isAir()) {
               Entity Entity_Class = M;
               new MobPlaceBlock(Entity_Class, this.Block_Type, Fwd_Block_Pos.below());
               M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
               this.isDoing = true;
            }
         }
      }
   }

   boolean isMonsterAtTargetLvl(Mob M, Entity Tgt) {
      double M_Y = M.blockPosition().getY();
      double TgtY = Tgt.blockPosition().getY();
      return Math.abs(TgtY - M_Y) < 2.0;
   }
}
