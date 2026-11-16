package com.nll.nightmareesm.helpers;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.ESMConfig.ConfigValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;

public class ConfigHelper {
   private static final class CachedPatterns {
      final int epoch;
      final String[] normalizedPatterns;
      final Set<String> exactSet;

      CachedPatterns(int epoch, List<? extends String> rawList) {
         this.epoch = epoch;
         if (rawList == null || rawList.isEmpty()) {
            this.normalizedPatterns = new String[0];
            this.exactSet = Set.of();
         } else {
            String[] patterns = new String[rawList.size()];
            for (int i = 0; i < rawList.size(); i++) {
               String item = rawList.get(i);
               patterns[i] = item == null ? "" : item.replace(" ", "_").trim().toLowerCase(Locale.ROOT);
            }
            this.normalizedPatterns = patterns;
            this.exactSet = Set.of(patterns);
         }
      }
   }

   private static final class BlockCacheBucket {
      final int epoch;
      final ConcurrentHashMap<Block, Boolean> exactResults = new ConcurrentHashMap<>();
      final ConcurrentHashMap<Block, Boolean> containsResults = new ConcurrentHashMap<>();

      BlockCacheBucket(int epoch) {
         this.epoch = epoch;
      }
   }

   private static final class EntityCacheBucket {
      final int epoch;
      final ConcurrentHashMap<EntityType<?>, Boolean> exactResults = new ConcurrentHashMap<>();
      final ConcurrentHashMap<EntityType<?>, Boolean> containsResults = new ConcurrentHashMap<>();

      EntityCacheBucket(int epoch) {
         this.epoch = epoch;
      }
   }

   private static final Map<ConfigValue<List<? extends String>>, CachedPatterns> PATTERN_CACHE = new ConcurrentHashMap<>();
   private static final Map<ConfigValue<List<? extends String>>, BlockCacheBucket> BLOCK_CACHE = new ConcurrentHashMap<>();
   private static final Map<ConfigValue<List<? extends String>>, EntityCacheBucket> ENTITY_CACHE = new ConcurrentHashMap<>();

   private static CachedPatterns getPatterns(ConfigValue<List<? extends String>> config) {
      int epoch = ESMConfig.getConfigEpoch();
      CachedPatterns cached = PATTERN_CACHE.get(config);
      if (cached != null && cached.epoch == epoch) {
         return cached;
      }
      return PATTERN_CACHE.compute(config, (k, existing) ->
         existing != null && existing.epoch == epoch ? existing : new CachedPatterns(epoch, k.get())
      );
   }

   private static EntityCacheBucket getEntityBucket(ConfigValue<List<? extends String>> config) {
      int epoch = ESMConfig.getConfigEpoch();
      EntityCacheBucket bucket = ENTITY_CACHE.get(config);
      if (bucket != null && bucket.epoch == epoch) {
         return bucket;
      }
      return ENTITY_CACHE.compute(config, (k, existing) ->
         existing != null && existing.epoch == epoch ? existing : new EntityCacheBucket(epoch)
      );
   }

   private static BlockCacheBucket getBlockBucket(ConfigValue<List<? extends String>> config) {
      int epoch = ESMConfig.getConfigEpoch();
      BlockCacheBucket bucket = BLOCK_CACHE.get(config);
      if (bucket != null && bucket.epoch == epoch) {
         return bucket;
      }
      return BLOCK_CACHE.compute(config, (k, existing) ->
         existing != null && existing.epoch == epoch ? existing : new BlockCacheBucket(epoch)
      );
   }

   public static boolean matchesPreNormalized(String normalizedValue, String[] normalizedPatterns, boolean containsSearch) {
      if (normalizedValue == null || normalizedPatterns == null || normalizedPatterns.length == 0) {
         return false;
      }
      if (containsSearch) {
         for (String pattern : normalizedPatterns) {
            if (!pattern.isEmpty() && normalizedValue.contains(pattern)) {
               return true;
            }
         }
         return false;
      }
      for (String pattern : normalizedPatterns) {
         if (normalizedValue.equals(pattern)) {
            return true;
         }
      }
      return false;
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Mob Mob_Val, boolean ContainsSearch) {
      return ConfigHasValue(Config, (Entity)Mob_Val, ContainsSearch);
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Entity Entity_Val, boolean ContainsSearch) {
      if (Config == null || Entity_Val == null) {
         return false;
      }
      EntityType<?> entityType = Entity_Val.getType();
      if (entityType == null) {
         return ConfigHasValue(Config, Entity_Val.getName().getString(), ContainsSearch);
      }
      EntityCacheBucket bucket = getEntityBucket(Config);
      ConcurrentHashMap<EntityType<?>, Boolean> map = ContainsSearch ? bucket.containsResults : bucket.exactResults;
      Boolean cached = map.get(entityType);
      if (cached != null) {
         return cached;
      }
      Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
      CachedPatterns patterns = getPatterns(Config);
      boolean matched;
      if (id != null) {
         String path = id.getPath().toLowerCase(Locale.ROOT);
         String full = id.toString().toLowerCase(Locale.ROOT);
         if (!ContainsSearch) {
            matched = patterns.exactSet.contains(path) || patterns.exactSet.contains(full);
         } else {
            matched = matchesPreNormalized(full, patterns.normalizedPatterns, true)
               || matchesPreNormalized(path, patterns.normalizedPatterns, true);
         }
      } else {
         matched = ConfigHasValue(Config, Entity_Val.getName().getString(), ContainsSearch);
      }
      map.putIfAbsent(entityType, matched);
      return matched;
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, Block Block_Val, boolean ContainsSearch) {
      if (Config == null || Block_Val == null) {
         return false;
      }
      BlockCacheBucket bucket = getBlockBucket(Config);
      ConcurrentHashMap<Block, Boolean> map = ContainsSearch ? bucket.containsResults : bucket.exactResults;
      Boolean cached = map.get(Block_Val);
      if (cached != null) {
         return cached;
      }
      Identifier blockId = BuiltInRegistries.BLOCK.getKey(Block_Val);
      CachedPatterns patterns = getPatterns(Config);
      boolean matched = false;
      if (blockId != null) {
         String key = blockId.toString().toLowerCase(Locale.ROOT);
         String path = blockId.getPath().toLowerCase(Locale.ROOT);
         if (!ContainsSearch) {
            matched = patterns.exactSet.contains(key) || patterns.exactSet.contains(path);
         } else {
            matched = matchesPreNormalized(key, patterns.normalizedPatterns, true)
               || matchesPreNormalized(path, patterns.normalizedPatterns, true);
         }
      }
      map.putIfAbsent(Block_Val, matched);
      return matched;
   }

   public static boolean ConfigHasValue(ConfigValue<List<? extends String>> Config, String Val, boolean ContainsSearch) {
      if (Config == null || Val == null) {
         return false;
      }
      String normalized = Val.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
      CachedPatterns patterns = getPatterns(Config);
      if (!ContainsSearch) {
         return patterns.exactSet.contains(normalized);
      }
      return matchesPreNormalized(normalized, patterns.normalizedPatterns, true);
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
