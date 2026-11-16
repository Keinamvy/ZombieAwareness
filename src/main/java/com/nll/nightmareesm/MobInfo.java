package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class MobInfo {
   public static ServerLevel GetMobServerLevel(Mob M) {
      if (M != null && M.level() instanceof ServerLevel sl) {
         return sl;
      }
      return null;
   }

   public static Level GetMobLevel(Mob M) {
      return M != null ? M.level() : null;
   }

   public static ServerLevel GetMobServerLevel(ServerPlayer S) {
      return S != null ? S.level() : null;
   }

   public static Level GetMobLevel(ServerPlayer S) {
      return S != null ? S.level() : null;
   }

   public static boolean canAccessServerBlock(boolean hasLevel, boolean isClientSide, boolean isLoaded) {
      return hasLevel && !isClientSide && isLoaded;
   }

   public static boolean isServerPosLoaded(Level level, BlockPos pos) {
      return level != null && pos != null && canAccessServerBlock(true, level.isClientSide(), level.isLoaded(pos));
   }

   public static boolean isSameDimension(Entity a, Entity b) {
      return a != null && b != null && a.level() != null && a.level() == b.level();
   }
}
