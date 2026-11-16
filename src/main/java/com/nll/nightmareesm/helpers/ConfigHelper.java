package com.nll.nightmareesm.helpers;

import com.nll.nightmareesm.Clocker;
import com.nll.nightmareesm.ESMConfig.ConfigValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigHelper {
   private static final Logger LOGGER = LogManager.getLogger();

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Mob Mob_Val, boolean ContainsSearch) {
      return ConfigHasValue(Config, (Entity)Mob_Val, ContainsSearch);
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Entity Entity_Val, boolean ContainsSearch) {
      return ConfigHasValue(Config, Entity_Val.getName().getString().toLowerCase(), ContainsSearch);
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Block Block_Val, boolean ContainsSearch) {
      String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Block_Val).toString().toLowerCase(java.util.Locale.ROOT);
      if (ConfigHasValue(Config, key, ContainsSearch)) {
         return true;
      }
      String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Block_Val).getPath().toLowerCase(java.util.Locale.ROOT);
      return ConfigHasValue(Config, path, ContainsSearch);
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, String Val, boolean ContainsSearch) {
      Val = Val.trim().toLowerCase().replace(" ", "_");

      for (String ID : Config.get()) {
         if (ContainsSearch) {
            if (Val.contains(ID.replace(" ", "_").trim().toLowerCase())) {
               return true;
            }
         } else if (Val.compareTo(ID.trim().toLowerCase()) == 0) {
            return true;
         }
      }

      return false;
   }

   public static List<String> GetConfigValues(ConfigValue<List<? extends String>> Config) {
      List<? extends String> IDs = Config.get();
      List<String> Val_List = new ArrayList<>();

      for (String ID : IDs) {
         Val_List.add(ID.trim().toLowerCase());
      }

      return Val_List;
   }

   public static EntityType<?> GetRandomEntityTypeFromConfig(ConfigValue<List<? extends String>> Config) {
      String EntityTypeName = GetRandomValueFromConfig(Config);
      return EntityTypeName != null ? GetEntityTypeFromName(EntityTypeName) : null;
   }

   public static String GetRandomValueFromConfig(ConfigValue<List<? extends String>> Config) {
      if (Config == null) {
         return null;
      }
      List<? extends String> values = Config.get();
      if (values == null || values.isEmpty()) {
         return null;
      }
      int MobNum = Clocker.GetIndexFromTime(values.size());
      return values.get(MobNum);
   }

   public static EntityType<?> GetEntityTypeFromName(String EntityName) {
      if (EntityName == null || EntityName.isBlank()) {
         return null;
      }
      Identifier Monster_Identifier = Identifier.parse(EntityName);
      Optional<Reference<EntityType<?>>> Ent_Type = BuiltInRegistries.ENTITY_TYPE.get(Monster_Identifier);
      return Ent_Type.map(Reference::value).orElse(null);
   }
}
