package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Siege Engineer Zombie Build Ability — Horizontal Bridging.
 *
 * <p>Automatically places temporary {@link Blocks#COARSE_DIRT} blocks one block below and ahead of
 * a {@code za_engineer} zombie when it has a {@link LivingEntity} target at roughly the same Y
 * level ({@code |targetY - mobY| < 2}), between 2 and 24 blocks away horizontally, and faces an
 * open two-block drop. Does not require block items in mainhand.
 *
 * <p><b>Dependencies:</b> {@link MobPlaceBlock} (safe server-side placement, loaded-chunk check,
 * and 60s auto-decay / horde-end collapse tracking).
 */
public class MobBuildBridge {
   private static final Block BRIDGE_BLOCK = Blocks.COARSE_DIRT;
   public boolean isDoing;

   public MobBuildBridge(Mob M) {
      this.isDoing = tryBridge(M);
   }

   public static boolean tryBridge(Mob M) {
      if (M == null) {
         return false;
      }
      LivingEntity tgt = M.getTarget();
      if (tgt == null || !tgt.isAlive() || !MobInfo.isSameDimension(M, tgt)) {
         return false;
      }
      if (tgt instanceof Player player && (player.isCreative() || player.isSpectator())) {
         return false;
      }
      if (!isMonsterAtTargetLvl(M, tgt)) {
         return false;
      }
      double dx = tgt.getX() - M.getX();
      double dz = tgt.getZ() - M.getZ();
      double horizDistSq = dx * dx + dz * dz;
      if (horizDistSq < 4.0 || horizDistSq > 576.0) {
         return false;
      }

      Direction bridgeDir = Math.abs(dx) >= Math.abs(dz)
         ? (dx > 0.0 ? Direction.EAST : Direction.WEST)
         : (dz > 0.0 ? Direction.SOUTH : Direction.NORTH);
      M.lookAt(tgt, 30.0F, 30.0F);

      BlockPos mPos = M.blockPosition();
      BlockPos fwdBlockPos = mPos.relative(bridgeDir);
      Level currLevel = MobInfo.GetMobLevel(M);
      if (!MobInfo.isServerPosLoaded(currLevel, fwdBlockPos)) {
         return false;
      }
      BlockState currentState = currLevel.getBlockState(fwdBlockPos);
      if (!currentState.isAir() && !currentState.canBeReplaced()) {
         return false;
      }

      BlockPos blockBelowPos = fwdBlockPos.below();
      BlockPos blockBelowPos2 = blockBelowPos.below();
      if (!MobInfo.isServerPosLoaded(currLevel, blockBelowPos) || !MobInfo.isServerPosLoaded(currLevel, blockBelowPos2)) {
         return false;
      }
      BlockState blockBelowState = currLevel.getBlockState(blockBelowPos);
      BlockState blockBelowState2 = currLevel.getBlockState(blockBelowPos2);
      if ((blockBelowState.isAir() || blockBelowState.canBeReplaced()) && blockBelowState2.isAir()) {
         if (MobPlaceBlock.placeBlock(M, BRIDGE_BLOCK, blockBelowPos)) {
            M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
            return true;
         }
      }
      return false;
   }

   private static boolean isMonsterAtTargetLvl(Mob M, Entity Tgt) {
      double mY = M.blockPosition().getY();
      double tgtY = Tgt.blockPosition().getY();
      return Math.abs(tgtY - mY) < 2.0;
   }
}
