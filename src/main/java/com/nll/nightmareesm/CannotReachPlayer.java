package com.nll.nightmareesm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.Path;

public class CannotReachPlayer {
   public boolean CannotReach(Entity Entity_Class) {
      PathNavigation Path_Nav = null;
      Monster M = (Monster)Entity_Class;
      Path_Nav = M.getNavigation();
      if (Path_Nav != null) {
         Path Entity_Path = Path_Nav.getPath();
         return Entity_Path != null ? !Entity_Path.canReach() : false;
      } else {
         return false;
      }
   }
}
