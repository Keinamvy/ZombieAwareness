package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Siege Engineer Zombie Build Ability — Safe Block Placement & 60s Auto-Decay Tracker.
 *
 * <p>Shared placement primitive used by {@link MobBuildBridge} and {@link MobBuildUp} to place
 * temporary Siege Engineer blocks on the server only when the target position is loaded, empty or
 * replaceable, and does not overwrite unbreakable blocks, obsidian, or block entities.
 *
 * <p>Every placed block is tracked per {@link ServerLevel} with its placement tick and
 * automatically decays after {@link #DECAY_TICKS} (1,200 ticks = 60 seconds), or collapses
 * immediately when the active horde ends via {@link #collapseAllBlocks(ServerLevel)}.
 */
public class MobPlaceBlock {
   public static final long DECAY_TICKS = 1200L;

   public record TrackedEngineerBlock(Block placedBlock, long placedTick) {
   }

   private static final ConcurrentHashMap<ResourceKey<Level>, ConcurrentHashMap<BlockPos, TrackedEngineerBlock>> TRACKED_BLOCKS =
      new ConcurrentHashMap<>();

   public MobPlaceBlock(Entity Entity_Class, Block Block_Type, BlockPos Position) {
      placeBlock(Entity_Class, Block_Type, Position);
   }

   public static boolean placeBlock(Entity Entity_Class, Block Block_Type, BlockPos Position) {
      if (Entity_Class instanceof Mob Mob_Entity && Position != null && Block_Type != null) {
         Level Lvl = MobInfo.GetMobLevel(Mob_Entity);
         if (!MobInfo.isServerPosLoaded(Lvl, Position)) {
            return false;
         }
         BlockState existing = Lvl.getBlockState(Position);
         if ((!existing.isAir() && !existing.canBeReplaced())
               || existing.getDestroySpeed(Lvl, Position) < 0
               || existing.is(Blocks.OBSIDIAN)
               || existing.is(Blocks.CRYING_OBSIDIAN)
               || Lvl.getBlockEntity(Position) != null) {
            return false;
         }
         BlockPos immutablePos = Position.immutable();
         BlockState placedState = Block_Type.defaultBlockState();
         if (!Lvl.setBlockAndUpdate(immutablePos, placedState)) {
            return false;
         }
         if (Lvl instanceof ServerLevel serverLevel) {
            recordPlacedBlock(serverLevel, immutablePos, placedState, serverLevel.getGameTime());
         }
         SoundType Snd = placedState.getSoundType();
         Lvl.playSound(null, immutablePos, Snd.getPlaceSound(), SoundSource.BLOCKS, 0.6F, 0.8F);
         return true;
      }
      return false;
   }

   public static void recordPlacedBlock(ServerLevel level, BlockPos pos, BlockState state, long gameTime) {
      if (level == null || pos == null || state == null) {
         return;
      }
      recordTrackedEntry(level.dimension(), pos, state.getBlock(), gameTime);
   }

   public static void recordTrackedEntry(ResourceKey<Level> dimension, BlockPos pos, Block placedBlock, long gameTime) {
      if (dimension == null || pos == null) {
         return;
      }
      TRACKED_BLOCKS
         .computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
         .put(pos.immutable(), new TrackedEngineerBlock(placedBlock, gameTime));
   }

   public static boolean shouldDecayBlock(long placedTick, long currentTick) {
      return currentTick < placedTick || (currentTick - placedTick) >= DECAY_TICKS;
   }

   public static int tickDecay(ServerLevel level, long currentTick) {
      if (level == null) {
         return 0;
      }
      return tickDecay(
         level.dimension(),
         currentTick,
         level::isLoaded,
         (pos, expectedBlock) -> {
            if (level.getBlockState(pos).getBlock() == expectedBlock) {
               level.destroyBlock(pos, false);
               return true;
            }
            return false;
         }
      );
   }

   public static int tickDecay(
      ResourceKey<Level> dimension,
      long currentTick,
      Predicate<BlockPos> isLoaded,
      BiPredicate<BlockPos, Block> destroyIfMatches
   ) {
      if (dimension == null) {
         return 0;
      }
      ConcurrentHashMap<BlockPos, TrackedEngineerBlock> map = TRACKED_BLOCKS.get(dimension);
      if (map == null || map.isEmpty()) {
         return 0;
      }
      int removed = 0;
      for (Map.Entry<BlockPos, TrackedEngineerBlock> entry : map.entrySet()) {
         BlockPos pos = entry.getKey();
         TrackedEngineerBlock tracked = entry.getValue();
         if (shouldDecayBlock(tracked.placedTick(), currentTick) && isLoaded.test(pos)) {
            if (map.remove(pos, tracked) && destroyIfMatches.test(pos, tracked.placedBlock())) {
               removed++;
            }
         }
      }
      if (map.isEmpty()) {
         TRACKED_BLOCKS.remove(dimension, map);
      }
      return removed;
   }

   public static int collapseAllBlocks(ServerLevel level) {
      if (level == null) {
         return 0;
      }
      return collapseAllBlocks(
         level.dimension(),
         level::isLoaded,
         (pos, expectedBlock) -> {
            if (level.getBlockState(pos).getBlock() == expectedBlock) {
               level.destroyBlock(pos, false);
               return true;
            }
            return false;
         }
      );
   }

   public static int collapseAllBlocks(
      ResourceKey<Level> dimension,
      Predicate<BlockPos> isLoaded,
      BiPredicate<BlockPos, Block> destroyIfMatches
   ) {
      if (dimension == null) {
         return 0;
      }
      ConcurrentHashMap<BlockPos, TrackedEngineerBlock> map = TRACKED_BLOCKS.remove(dimension);
      if (map == null || map.isEmpty()) {
         return 0;
      }
      int collapsed = 0;
      for (Map.Entry<BlockPos, TrackedEngineerBlock> entry : map.entrySet()) {
         BlockPos pos = entry.getKey();
         TrackedEngineerBlock tracked = entry.getValue();
         if (isLoaded.test(pos) && destroyIfMatches.test(pos, tracked.placedBlock())) {
            collapsed++;
         }
      }
      return collapsed;
   }

   public static int getTrackedCount(ResourceKey<Level> dimension) {
      if (dimension == null) {
         return 0;
      }
      ConcurrentHashMap<BlockPos, TrackedEngineerBlock> map = TRACKED_BLOCKS.get(dimension);
      return map != null ? map.size() : 0;
   }

   public static void clearAll() {
      TRACKED_BLOCKS.clear();
   }
}
