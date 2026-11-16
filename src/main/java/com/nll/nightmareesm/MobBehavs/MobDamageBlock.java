package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobDamageBlock {
   private static final Logger LOGGER = LogManager.getLogger();

   public MobDamageBlock(Entity Entity_Class, Block Curr_Block, BlockPos Block_Pos) {
      if (Curr_Block != null && Entity_Class != null && Block_Pos != null) {
         boolean isExcluded = this.isExcludedBlock(Curr_Block);
         LOGGER.debug("BLOCK COMPARISON OF {} ::: IS_EXCLUDED = {}", Curr_Block, isExcluded);
         if (!isExcluded) {
            Level Lvl = Entity_Class.level();
            if (MobInfo.isServerPosLoaded(Lvl, Block_Pos)) {
               BlockState state = Lvl.getBlockState(Block_Pos);
               if (state.isAir() || state.getDestroySpeed(Lvl, Block_Pos) < 0 || Lvl.getBlockEntity(Block_Pos) != null) {
                  return;
               }
               Lvl.destroyBlock(Block_Pos, true, Entity_Class);
               try {
                  com.corosus.zombieawareness.ZAUtil.spawnOrBuffSenseAtPos(
                     Lvl,
                     new org.joml.Vector3d(Block_Pos.getX() + 0.5, Block_Pos.getY() + 0.5, Block_Pos.getZ() + 0.5),
                     com.corosus.zombieawareness.EnumSenseType.SOUND,
                     64,
                     true
                  );
               } catch (Throwable ignored) {
               }
            }
         }
      }
   }

   boolean isExcludedBlock(Block Curr_Block) {
      if (Curr_Block == null) {
         return true;
      }
      if (Curr_Block == Blocks.BEDROCK || Curr_Block == Blocks.BARRIER || Curr_Block == Blocks.OBSIDIAN
            || Curr_Block == Blocks.CRYING_OBSIDIAN || Curr_Block == Blocks.REINFORCED_DEEPSLATE
            || Curr_Block == Blocks.END_PORTAL_FRAME || Curr_Block == Blocks.COMMAND_BLOCK) {
         return true;
      }
      if (Curr_Block.defaultDestroyTime() < 0.0F) {
         return true;
      }
      return ConfigHelper.ConfigHasValue(ESMConfig.BlocksEntitiesCannotDigThrough, Curr_Block, true);
   }
}
