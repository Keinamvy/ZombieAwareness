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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpiderShootWeb {
   private static final Logger LOGGER = LoggerFactory.getLogger("SpiderShootWeb");

   public SpiderShootWeb(Mob M) {
      if (M == null) {
         return;
      }
      LivingEntity Tgt = M.getTarget();
      if (Tgt instanceof ServerPlayer Targeted_Player
            && MobInfo.isSameDimension(M, Targeted_Player)
            && this.SpiderIsInShootDistance(M, Targeted_Player)) {
         double New_RNG = new RNG().GetDouble(0.0, 100.0);
         if (New_RNG <= (Double)ESMConfig.SpiderShootWebChance.get()) {
            this.ShootWebAtPlayer(M, Targeted_Player);
         }
      }
   }

   boolean SpiderIsInShootDistance(Mob Spider_Entity, ServerPlayer Targeted_Player) {
      if (!MobInfo.isSameDimension(Spider_Entity, Targeted_Player)) {
         return false;
      }
      float Dist = Spider_Entity.distanceTo(Targeted_Player);
      return Dist < ((Integer)ESMConfig.SpiderShootWebDist.get()).intValue();
   }

   void ShootWebAtPlayer(Mob Spider_Entity, ServerPlayer Targeted_Player) {
      if (!MobInfo.isSameDimension(Spider_Entity, Targeted_Player)) {
         return;
      }
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
