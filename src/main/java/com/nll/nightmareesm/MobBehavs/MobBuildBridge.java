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
import net.minecraft.world.level.block.state.BlockState;

public class MobBuildBridge {
   Block Block_Type = Blocks.COBBLESTONE;
   public boolean isDoing;

   public MobBuildBridge(Mob M) {
      Entity Tgt = M.getTarget();
      if (Tgt != null && Tgt instanceof ServerPlayer Server_Player && !Server_Player.isCreative() && !Server_Player.isSpectator() && this.isMonsterAtTargetLvl(M, Tgt)) {
         double horizDist = Math.hypot(M.getX() - Tgt.getX(), M.getZ() - Tgt.getZ());
         if (horizDist >= 2.0 && horizDist <= 24.0) {
            BlockPos M_Pos = M.blockPosition();
            M.lookAt(Tgt, 30.0F, 30.0F);
            BlockPos Fwd_Block_Pos = M_Pos.relative(M.getDirection());
            Level Curr_Level = MobInfo.GetMobLevel(M);
            if (Curr_Level != null) {
               BlockState Current_State = Curr_Level.getBlockState(Fwd_Block_Pos);
               if (Current_State.isAir() || Current_State.canBeReplaced()) {
                  BlockPos Block_Below_Pos = Fwd_Block_Pos.below();
                  BlockPos Block_Below_Pos2 = Block_Below_Pos.below();
                  BlockState Block_Below_State = Curr_Level.getBlockState(Block_Below_Pos);
                  BlockState Block_Below_State2 = Curr_Level.getBlockState(Block_Below_Pos2);
                  if (Block_Below_State.isAir() && Block_Below_State2.isAir()) {
                     Entity Entity_Class = M;
                     new MobPlaceBlock(Entity_Class, this.Block_Type, Fwd_Block_Pos.below());
                     M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
                     this.isDoing = true;
                  }
               }
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
