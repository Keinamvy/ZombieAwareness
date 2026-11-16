package com.nll.nightmareesm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

public class CannotReachPlayer {
   public boolean CannotReach(Entity Entity_Class) {
      return cannotReach(Entity_Class);
   }

   public static boolean cannotReach(Entity Entity_Class) {
      if (!(Entity_Class instanceof Mob M)) {
         return false;
      }
      PathNavigation Path_Nav = M.getNavigation();
      if (Path_Nav != null) {
         Path Entity_Path = Path_Nav.getPath();
         return Entity_Path != null && !Entity_Path.canReach();
      }
      return false;
   }
}
