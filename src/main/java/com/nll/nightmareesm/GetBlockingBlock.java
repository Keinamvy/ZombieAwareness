package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class GetBlockingBlock {
   public BlockPos Current_Position;
   public Block Current_Block;
   public boolean isBlocking;
   public BlockPos Feet_Position;
   public Block Feet_Block;
   public boolean isFootBlocked;

   public GetBlockingBlock(Entity Entity_Class) {
      if (!(Entity_Class instanceof Mob mob)) {
         return;
      }
      BlockPos fwdBlock = computeForwardBlockPos(mob);
      this.Current_Position = fwdBlock;
      Level Curr_Level = MobInfo.GetMobLevel(mob);
      if (MobInfo.isServerPosLoaded(Curr_Level, fwdBlock)) {
         this.Current_Block = Curr_Level.getBlockState(fwdBlock).getBlock();
         this.isBlocking = !BlockInfo.isAir(this.Current_Block) && !BlockInfo.isLiquid(this.Current_Block);
      }
      this.Feet_Position = fwdBlock.below();
      if (MobInfo.isServerPosLoaded(Curr_Level, this.Feet_Position)) {
         this.Feet_Block = Curr_Level.getBlockState(this.Feet_Position).getBlock();
         this.isFootBlocked = !BlockInfo.isAir(this.Feet_Block) && !BlockInfo.isLiquid(this.Feet_Block);
      }
   }

   public static BlockPos computeForwardBlockPos(Mob mob) {
      Vec3 entityEyePos = mob.getEyePosition();
      Vec3 dir = mob.getForward();
      if (mob.getTarget() == null && mob.getNavigation() != null && mob.getNavigation().getTargetPos() != null) {
         Vec3 targetVec = Vec3.atCenterOf(mob.getNavigation().getTargetPos()).subtract(entityEyePos);
         if (targetVec.horizontalDistanceSqr() > 0.001) {
            dir = new Vec3(targetVec.x, 0, targetVec.z).normalize();
         }
      }
      Vec3 fwd = dir.normalize().multiply(0.75, 0.75, 0.75);
      return BlockPos.containing(entityEyePos.add(fwd));
   }

   public static boolean isWallBlocked(Entity entity) {
      if (!(entity instanceof Mob mob)) {
         return false;
      }
      Level lvl = MobInfo.GetMobLevel(mob);
      BlockPos fwdBlock = computeForwardBlockPos(mob);
      if (!MobInfo.isServerPosLoaded(lvl, fwdBlock)) {
         return false;
      }
      Block faceBlock = lvl.getBlockState(fwdBlock).getBlock();
      if (BlockInfo.isAir(faceBlock) || BlockInfo.isLiquid(faceBlock)) {
         return false;
      }
      BlockPos feetPos = fwdBlock.below();
      if (!MobInfo.isServerPosLoaded(lvl, feetPos)) {
         return false;
      }
      Block feetBlock = lvl.getBlockState(feetPos).getBlock();
      return !BlockInfo.isAir(feetBlock) && !BlockInfo.isLiquid(feetBlock);
   }

   public static boolean canQueryBlock(boolean hasLevel, boolean isClientSide, boolean isLoaded) {
      return MobInfo.canAccessServerBlock(hasLevel, isClientSide, isLoaded);
   }
}
