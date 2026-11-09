package com.nll.nightmareesm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class MobInfo {
   public static ServerLevel GetMobServerLevel(Mob M) {
      return (ServerLevel)M.level();
   }

   public static Level GetMobLevel(Mob M) {
      return M.level();
   }

   public static ServerLevel GetMobServerLevel(ServerPlayer S) {
      return S.level();
   }

   public static Level GetMobLevel(ServerPlayer S) {
      return S.level();
   }
}
