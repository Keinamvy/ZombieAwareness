package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.BlockInfo;
import com.nll.nightmareesm.GetBlockingBlock;
import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class MobDig {
   public MobDig(Entity Entity_Class) {
      tryDig(Entity_Class);
   }

   public static void tryDig(Entity Entity_Class) {
      if (!(Entity_Class instanceof Monster M) || M.isSwinging()) {
         return;
      }
      // Unobstructed zombies actively following a path skip block probes.
      if (!M.horizontalCollision && (M.getNavigation() == null || M.getNavigation().isInProgress())) {
         return;
      }
      LivingEntity Tgt = M.getTarget();
      boolean targetIsPlayer = Tgt instanceof ServerPlayer Server_Player && !Server_Player.isCreative() && !Server_Player.isSpectator();
      boolean navigatingToSense = Tgt == null && M.getNavigation() != null && M.getNavigation().getTargetPos() != null;
      if (targetIsPlayer || navigatingToSense) {
         Level lvl = MobInfo.GetMobLevel(M);
         BlockPos facePos = GetBlockingBlock.computeForwardBlockPos(M);
         Block faceBlock = MobInfo.isServerPosLoaded(lvl, facePos) ? lvl.getBlockState(facePos).getBlock() : null;
         BlockPos feetPos = facePos.below();
         Block feetBlock = MobInfo.isServerPosLoaded(lvl, feetPos) ? lvl.getBlockState(feetPos).getBlock() : null;

         boolean isFaceBlocked = faceBlock != null && !BlockInfo.isAir(faceBlock);
         boolean isFeetBlocked = feetBlock != null && !BlockInfo.isAir(feetBlock);
         if (isFaceBlocked) {
            M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
            MobDamageBlock.damageBlock(Entity_Class, faceBlock, facePos);
         }
         if (isFeetBlocked) {
            MobDamageBlock.damageBlock(Entity_Class, feetBlock, feetPos);
         }
      }
   }
}
