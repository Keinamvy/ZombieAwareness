package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobDamageBlock {
   private static final Logger LOGGER = LogManager.getLogger();

   public MobDamageBlock(Entity Entity_Class, Block Curr_Block, BlockPos Block_Pos) {
      if (Curr_Block != null) {
         boolean isExcluded = this.isExcludedBlock(Curr_Block);
         LOGGER.info("BLOCK COMPARISON OF " + Curr_Block.getName().getString().toLowerCase() + " ::: IS_EXCLUDED = " + isExcluded);
         if (!isExcluded) {
            new MobPlaceBlock(Entity_Class, Blocks.AIR, Block_Pos);
            Level Lvl = Entity_Class.level();
            if (Lvl != null && !Lvl.isClientSide()) {
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
      return ConfigHelper.ConfigHasValue(ESMConfig.BlocksEntitiesCannotDigThrough, Curr_Block, true);
   }
}
