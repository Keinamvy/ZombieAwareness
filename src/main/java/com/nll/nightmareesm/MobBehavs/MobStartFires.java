package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.RNG;
import com.nll.nightmareesm.helpers.ConfigHelper;
import com.corosus.zombieawareness.mixin.FireBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobStartFires {
   private static final Logger LOGGER = LogManager.getLogger();
   String[] Excluded_Tags = new String[]{
      "_sign",
      "_bed",
      "pressure_plate",
      "_button",
      "sugar_cane",
      "juke",
      "trapdoor",
      "enchanting",
      "brewing",
      "potted_",
      "skull",
      "_head",
      "chest",
      "dropper",
      "prismarine",
      "book",
      "grass",
      "snow",
      "flower",
      "sapling",
      "bush",
      "stem",
      "dragon",
      "carrots",
      "potatoes",
      "carpet",
      "banner",
      "structure",
      "compost",
      "piston",
      "log"
   };

   public MobStartFires(Mob M) {
      double New_RNG = new RNG().GetDouble(0.0, 100.0);
      double Chance_Burn_Wood = (Double)ESMConfig.ChanceOfZombieLightFire.get();
      if (New_RNG < Chance_Burn_Wood) {
         this.StartFire(M);
      }
   }

   void StartFire(Mob M) {
      if (M == null) {
         return;
      }
      BlockPos Block_Pos = M.blockPosition();
      LivingEntity Living_Entity = M.getTarget();
      if (Living_Entity instanceof ServerPlayer Server_Player && MobInfo.isSameDimension(M, Server_Player)) {
         Level Lvl = MobInfo.GetMobLevel(M);
         if (Lvl == null || Lvl.isClientSide()) {
            return;
         }
         boolean isDone = false;

         for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
               for (int z = -1; z <= 1; z++) {
                  if (!isDone) {
                     BlockPos Curr_Block_Pos = Block_Pos.offset(x, y, z);
                     if (!arePosAndNeighborsLoaded(Lvl, Curr_Block_Pos)) {
                        continue;
                     }
                     BlockState CurrBlock_State = Lvl.getBlockState(Curr_Block_Pos);
                     Block B = CurrBlock_State.getBlock();
                     boolean canBurn = ((FireBlockAccessor)Blocks.FIRE).invokeCanBurn(CurrBlock_State) || BaseFireBlock.canBePlacedAt(Lvl, Curr_Block_Pos, Direction.NORTH);
                     if (canBurn && this.isNotExcluded(B)) {
                        this.LightFire(Server_Player, Lvl, Curr_Block_Pos, CurrBlock_State);
                        isDone = true;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean arePosAndNeighborsLoaded(Level lvl, BlockPos pos) {
      if (!MobInfo.isServerPosLoaded(lvl, pos)) {
         return false;
      }
      for (Direction dir : Direction.values()) {
         if (!lvl.isLoaded(pos.relative(dir))) {
            return false;
         }
      }
      return true;
   }

   boolean isNotExcluded(Block B) {
      return ConfigHelper.ConfigHasValue(ESMConfig.FlammableBlocksWhitelist, B, true);
   }

   void LightFire(ServerPlayer Curr_Tgt, Level Lvl, BlockPos Fire_Pos, BlockState Curr_Blockstate) {
      if (!MobInfo.isServerPosLoaded(Lvl, Fire_Pos)) {
         return;
      }
      Lvl.playLocalSound(Fire_Pos.getX(), Fire_Pos.getY(), Fire_Pos.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
      BlockState Block_State_Fire = BaseFireBlock.getState(Lvl, Fire_Pos);
      Lvl.setBlock(Fire_Pos, Block_State_Fire, 3);
   }
}
