package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.PositionInfo;
import com.nll.nightmareesm.SnapToBlockCenter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Isolated Zombie Build Ability — Vertical Pillar Building.
 *
 * <p>Places a {@link Blocks#COBBLESTONE} pillar block and steps the zombie upward when its
 * survival player target is 1.5 to 24 blocks above it and within 16 blocks horizontally, provided
 * headroom above the mob is clear and target chunks are loaded.
 *
 * <p><b>Dependencies:</b> {@link MobPlaceBlock} (safe server-side block placement),
 * {@link SnapToBlockCenter} (re-centers the mob onto the block after stepping upward), and
 * {@link com.nll.nightmareesm.PositionInfo}.
 *
 * <p><b>Current Status:</b> Intentionally isolated and disabled while {@code BRIDGE_PILLAR} returns
 * {@code false} in {@link com.nll.nightmareesm.nightmareesm#canUseBehaviorForStage(String, int)} and
 * {@link com.nll.nightmareesm.ESMConfig#AllowZombieBuilding} defaults to {@code false}. Retained so
 * vertical pillaring can be re-enabled from {@code nightmareesm.LivingMobTick} in the future.
 */
public class MobBuildUp {
   private static final Logger LOGGER = LogManager.getLogger();
   int BuildUpDist = 4;
   Block Block_Type = Blocks.COBBLESTONE;
   public boolean isDoing;

   public MobBuildUp(Mob M) {
      if (M == null) {
         return;
      }
      Entity Tgt = M.getTarget();
      if (Tgt instanceof ServerPlayer Server_Player && MobInfo.isSameDimension(M, Server_Player)
            && !Server_Player.isCreative() && !Server_Player.isSpectator()
            && this.isCloseEnough(M, Tgt) && this.isPlayerHighEnough(M, Tgt)) {
         Entity Entity_Class = M;
         BlockPos M_BlockPos = M.blockPosition();
         BlockPos M_BlockPos_Above = M_BlockPos.above();
         ServerLevel Server_Level = MobInfo.GetMobServerLevel(M);
         if (MobInfo.isServerPosLoaded(Server_Level, M_BlockPos_Above)) {
            BlockState Block_State = Server_Level.getBlockState(M_BlockPos_Above);
            if (Block_State.isAir() || Block_State.canBeReplaced()) {
               Vec3 Norm_Build_Dir = this.GetBuildDir(M, Tgt);
               BlockPos New_Pos = PositionInfo.OffsetPos(M_BlockPos, Norm_Build_Dir.x, Norm_Build_Dir.y, Norm_Build_Dir.z);
               if (!MobInfo.isServerPosLoaded(Server_Level, New_Pos)) {
                  return;
               }
               BlockState targetState = Server_Level.getBlockState(New_Pos);
               if (targetState.isAir() || targetState.canBeReplaced()) {
                  new MobPlaceBlock(Entity_Class, this.Block_Type, New_Pos);
                  M.setPos(M.blockPosition().getX(), M.blockPosition().getY() + 2, M.blockPosition().getZ());
                  new SnapToBlockCenter(Entity_Class);
                  M.swing(M.getUsedItemHand(), SwingAnimation.DEFAULT);
                  this.isDoing = true;
               }
            }
         }
      }
   }

   boolean isCloseEnough(Mob M, Entity Tgt) {
      double horizontalDist = Math.hypot(M.getX() - Tgt.getX(), M.getZ() - Tgt.getZ());
      return horizontalDist <= 16.0;
   }

   boolean isPlayerHighEnough(Mob M, Entity Tgt) {
      double verticalDist = Tgt.getY() - M.getY();
      return verticalDist >= 1.5 && verticalDist <= 24.0;
   }

   boolean isBelowPlayer(Mob M, Entity Tgt) {
      return Tgt.getY() - M.getY() >= 1.5;
   }

   Vec3 GetBuildDir(Mob M, Entity Tgt) {
      BlockPos Tgt_Block_Pos = Tgt.blockPosition();
      float Tgt_X = Tgt_Block_Pos.getX();
      float Tgt_Y = Tgt_Block_Pos.getY();
      float Tgt_Z = Tgt_Block_Pos.getZ();
      BlockPos M_Block_Pos = M.blockPosition();
      float Z_X = M_Block_Pos.getX();
      float Z_Y = M_Block_Pos.getY();
      float Z_Z = M_Block_Pos.getZ();
      float Diff_X = Tgt_X - Z_X;
      float Diff_Y = Tgt_Y - Z_Y;
      float Diff_Z = Tgt_Z - Z_Z;
      Vec3 BlockDir = new Vec3(Diff_X, Diff_Y, Diff_Z);
      return BlockDir.normalize();
   }
}
