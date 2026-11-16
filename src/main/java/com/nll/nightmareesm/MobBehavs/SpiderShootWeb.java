package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SpiderShootWeb {
   public SpiderShootWeb(Mob M) {
      tryShoot(M);
   }

   public static void tryShoot(Mob M) {
      if (M == null) {
         return;
      }
      LivingEntity Tgt = M.getTarget();
      if (Tgt instanceof ServerPlayer Targeted_Player
            && MobInfo.isSameDimension(M, Targeted_Player)
            && isInShootDistance(M, Targeted_Player)) {
         if (RNG.getDouble(0.0, 100.0) <= (Double)ESMConfig.SpiderShootWebChance.get()) {
            shootWebAtPlayer(M, Targeted_Player);
         }
      }
   }

   private static boolean isInShootDistance(Mob Spider_Entity, ServerPlayer Targeted_Player) {
      int maxDist = (Integer)ESMConfig.SpiderShootWebDist.get();
      return Spider_Entity.distanceToSqr(Targeted_Player) < (double)(maxDist * maxDist);
   }

   private static void shootWebAtPlayer(Mob Spider_Entity, ServerPlayer Targeted_Player) {
      Level Lvl = MobInfo.GetMobLevel(Spider_Entity);
      BlockPos targetPos = Targeted_Player.blockPosition();
      if (!MobInfo.isServerPosLoaded(Lvl, targetPos)) {
         return;
      }
      BlockState Player_Block_State = Lvl.getBlockState(targetPos);
      if (Player_Block_State.isAir()) {
         Lvl.setBlock(targetPos, Blocks.COBWEB.defaultBlockState(), 3);
      }
   }
}
