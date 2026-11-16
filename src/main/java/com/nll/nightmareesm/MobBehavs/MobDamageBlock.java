package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.helpers.ConfigHelper;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MobDamageBlock {
   private static final class ExcludedCache {
      final int epoch;
      final ConcurrentHashMap<Block, Boolean> map = new ConcurrentHashMap<>();

      ExcludedCache(int epoch) {
         this.epoch = epoch;
      }
   }

   private static volatile ExcludedCache excludedCache = new ExcludedCache(-1);

   public MobDamageBlock(Entity Entity_Class, Block Curr_Block, BlockPos Block_Pos) {
      damageBlock(Entity_Class, Curr_Block, Block_Pos);
   }

   public static void damageBlock(Entity Entity_Class, Block Curr_Block, BlockPos Block_Pos) {
      if (Curr_Block == null || Entity_Class == null || Block_Pos == null) {
         return;
      }
      if (Curr_Block.defaultBlockState().isAir() || Curr_Block.defaultDestroyTime() < 0.0F) {
         return;
      }
      if (isExcludedBlock(Curr_Block)) {
         return;
      }
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

   public static boolean isExcludedBlock(Block Curr_Block) {
      if (Curr_Block == null || Curr_Block.defaultBlockState().isAir() || Curr_Block.defaultDestroyTime() < 0.0F) {
         return true;
      }
      int epoch = ESMConfig.getConfigEpoch();
      ExcludedCache cache = excludedCache;
      if (cache.epoch != epoch) {
         cache = new ExcludedCache(epoch);
         excludedCache = cache;
      }
      Boolean cached = cache.map.get(Curr_Block);
      if (cached != null) {
         return cached;
      }
      boolean excluded = Curr_Block == Blocks.BEDROCK || Curr_Block == Blocks.BARRIER || Curr_Block == Blocks.OBSIDIAN
            || Curr_Block == Blocks.CRYING_OBSIDIAN || Curr_Block == Blocks.REINFORCED_DEEPSLATE
            || Curr_Block == Blocks.END_PORTAL_FRAME || Curr_Block == Blocks.COMMAND_BLOCK
            || ConfigHelper.ConfigHasValue(ESMConfig.BlocksEntitiesCannotDigThrough, Curr_Block, true);
      cache.map.putIfAbsent(Curr_Block, excluded);
      return excluded;
   }
}
