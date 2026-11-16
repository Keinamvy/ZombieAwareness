package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.SnapToBlockCenter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Siege Engineer Zombie Build Ability — Vertical Pillar Building.
 *
 * <p>Automatically places a temporary {@link Blocks#COBBLESTONE} pillar block beneath a
 * {@code za_engineer} zombie and steps it upward when it has an elevated {@link LivingEntity}
 * target 1.5 to 24 blocks above it and within 16 blocks horizontally, provided headroom above the
 * mob is clear and target chunks are loaded. Does not require block items in mainhand.
 *
 * <p><b>Dependencies:</b> {@link MobPlaceBlock} (safe server-side placement &amp; 60s auto-decay
 * tracking) and {@link SnapToBlockCenter} (centers the mob on top of the pillar block).
 */
public class MobBuildUp {
   private static final Block PILLAR_BLOCK = Blocks.COBBLESTONE;
   public boolean isDoing;

   public MobBuildUp(Mob M) {
      this.isDoing = tryBuildUp(M);
   }

   public static boolean tryBuildUp(Mob M) {
      if (M == null || !M.onGround()) {
         return false;
      }
      LivingEntity tgt = M.getTarget();
      if (tgt == null || !tgt.isAlive() || !MobInfo.isSameDimension(M, tgt)) {
         return false;
      }
      if (tgt instanceof Player player && (player.isCreative() || player.isSpectator())) {
         return false;
      }
      if (!isCloseEnough(M, tgt) || !isTargetHighEnough(M, tgt)) {
         return false;
      }

      ServerLevel serverLevel = MobInfo.GetMobServerLevel(M);
      BlockPos mobFeetPos = M.blockPosition();
      BlockPos headClearance1 = mobFeetPos.above();
      BlockPos headClearance2 = mobFeetPos.above(2);
      if (!MobInfo.isServerPosLoaded(serverLevel, mobFeetPos)
            || !MobInfo.isServerPosLoaded(serverLevel, headClearance1)
            || !MobInfo.isServerPosLoaded(serverLevel, headClearance2)) {
         return false;
      }

      BlockState feetState = serverLevel.getBlockState(mobFeetPos);
      BlockState above1State = serverLevel.getBlockState(headClearance1);
      BlockState above2State = serverLevel.getBlockState(headClearance2);
      if ((feetState.isAir() || feetState.canBeReplaced())
            && (above1State.isAir() || above1State.canBeReplaced())
            && (above2State.isAir() || above2State.canBeReplaced())) {
         if (MobPlaceBlock.placeBlock(M, PILLAR_BLOCK, mobFeetPos)) {
            M.setPos(mobFeetPos.getX() + 0.5, mobFeetPos.getY() + 1.0, mobFeetPos.getZ() + 0.5);
            SnapToBlockCenter.snap(M);
            M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
            return true;
         }
      }
      return false;
   }

   private static boolean isCloseEnough(Mob M, Entity Tgt) {
      double dx = M.getX() - Tgt.getX();
      double dz = M.getZ() - Tgt.getZ();
      return (dx * dx + dz * dz) <= 256.0;
   }

   private static boolean isTargetHighEnough(Mob M, Entity Tgt) {
      double verticalDist = Tgt.getY() - M.getY();
      return verticalDist >= 1.5 && verticalDist <= 24.0;
   }
}
