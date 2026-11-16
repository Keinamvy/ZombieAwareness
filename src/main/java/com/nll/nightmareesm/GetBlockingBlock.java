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
      Vec3 Entity_Eye_Pos = Entity_Class.getEyePosition();
      Vec3 dir = Entity_Class.getForward();
      if (mob.getTarget() == null && mob.getNavigation() != null && mob.getNavigation().getTargetPos() != null) {
         Vec3 targetVec = Vec3.atCenterOf(mob.getNavigation().getTargetPos()).subtract(Entity_Eye_Pos);
         if (targetVec.horizontalDistanceSqr() > 0.001) {
            dir = new Vec3(targetVec.x, 0, targetVec.z).normalize();
         }
      }
      Vec3 Fwd = dir.normalize().multiply(0.75, 0.75, 0.75);
      Vec3 Fwd_Block_Pos = Entity_Eye_Pos.add(Fwd);
      BlockPos Fwd_Block = BlockPos.containing(Fwd_Block_Pos);
      this.Current_Position = Fwd_Block;
      Level Curr_Level = MobInfo.GetMobLevel(mob);
      if (MobInfo.isServerPosLoaded(Curr_Level, Fwd_Block)) {
         this.Current_Block = Curr_Level.getBlockState(Fwd_Block).getBlock();
         this.isBlocking = !BlockInfo.isAir(this.Current_Block) && !BlockInfo.isLiquid(this.Current_Block);
      }
      this.Feet_Position = Fwd_Block.below();
      if (MobInfo.isServerPosLoaded(Curr_Level, this.Feet_Position)) {
         this.Feet_Block = Curr_Level.getBlockState(this.Feet_Position).getBlock();
         this.isFootBlocked = !BlockInfo.isAir(this.Feet_Block) && !BlockInfo.isLiquid(this.Feet_Block);
      }
   }

   public static boolean canQueryBlock(boolean hasLevel, boolean isClientSide, boolean isLoaded) {
      return MobInfo.canAccessServerBlock(hasLevel, isClientSide, isLoaded);
   }
}
