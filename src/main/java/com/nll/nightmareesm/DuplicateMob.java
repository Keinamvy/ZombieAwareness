package com.nll.nightmareesm;

import com.corosus.zombieawareness.ZAUtil;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DuplicateMob {
   private static final int SWARM_PLACEMENT_PROXIMITY = 2;

   public DuplicateMob(Mob M, int Count) {
      duplicate(M, Count);
   }

   public static void duplicate(Mob M, int Count) {
      if (M != null && Count > 0 && ZAUtil.isHordeMob(M) && ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, M, false)) {
         for (int i = 0; i < Count; i++) {
            cloneMob(M);
         }
      }
   }

   private static void cloneMob(Mob M) {
      ServerLevel Lvl = MobInfo.GetMobServerLevel(M);
      if (Lvl != null) {
         var server = Lvl.getServer();
         if (server == null) {
            return;
         }
         if (!server.isSameThread()) {
            // Never query ServerLevel.getBlockState() or spawn from a worldgen/worker thread (deadlocks with C2ME + Lithium).
            server.execute(() -> {
               if (M.isAlive() && !M.isRemoved() && Lvl.isLoaded(M.blockPosition())) {
                  spawnCloneOnServerThread(Lvl, M);
               }
            });
            return;
         }
         spawnCloneOnServerThread(Lvl, M);
      }
   }

   private static void spawnCloneOnServerThread(ServerLevel Lvl, Mob M) {
      if (Lvl == null || M == null || !M.isAlive() || M.isRemoved()) {
         return;
      }
      BlockPos pos = M.blockPosition();
      if (!Lvl.isLoaded(pos)) {
         return;
      }
      BlockPos Offset_Pos = getOffsetPosition(pos);
      if (isPositionGood(Lvl, Offset_Pos)) {
         M.getType().spawn(Lvl, new ItemStack(Items.DIRT), null, Offset_Pos, EntitySpawnReason.MOB_SUMMONED, true, false);
      }
   }

   static BlockPos getOffsetPosition(BlockPos Original_Pos) {
      int Rand_X_Off = RNG.getInt(-SWARM_PLACEMENT_PROXIMITY, SWARM_PLACEMENT_PROXIMITY + 1);
      int Rand_Z_Off = RNG.getInt(-SWARM_PLACEMENT_PROXIMITY, SWARM_PLACEMENT_PROXIMITY + 1);
      return PositionInfo.OffsetPos(Original_Pos, Rand_X_Off, 0.0, Rand_Z_Off);
   }

   static boolean isPositionGood(ServerLevel Lvl, BlockPos Position) {
      if (Lvl == null || Position == null) {
         return false;
      }
      var server = Lvl.getServer();
      if (server == null || !server.isSameThread()) {
         return false;
      }
      BlockPos abovePos = PositionInfo.OffsetPos(Position, 0.0, 1.0, 0.0);
      if (!Lvl.isLoaded(Position) || !Lvl.isLoaded(abovePos)) {
         return false;
      }
      boolean isBottomOK = Lvl.getBlockState(Position).isAir();
      boolean isTopOK = Lvl.getBlockState(abovePos).isAir();
      return isBottomOK && isTopOK;
   }
}
