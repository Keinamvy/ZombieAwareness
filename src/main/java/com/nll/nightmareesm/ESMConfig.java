package com.nll.nightmareesm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ESMConfig {
   private static final Logger LOGGER = LoggerFactory.getLogger("nightmareesm-config");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final List<ConfigValue<?>> ALL_CONFIGS = new ArrayList<>();

   public static class ConfigValue<T> {
      protected final String key;
      protected final String comment;
      protected final T defaultValue;
      protected T value;

      public ConfigValue(String key, String comment, T defaultValue) {
         this.key = key;
         this.comment = comment;
         this.defaultValue = defaultValue;
         this.value = defaultValue;
         ALL_CONFIGS.add(this);
      }

      public T get() {
         return this.value != null ? this.value : this.defaultValue;
      }

      public void set(T val) {
         this.value = val;
      }

      public String getKey() {
         return this.key;
      }

      public T getDefaultValue() {
         return this.defaultValue;
      }
   }

   public static class BooleanValue extends ConfigValue<Boolean> {
      public BooleanValue(String key, String comment, Boolean defaultValue) {
         super(key, comment, defaultValue);
      }
   }

   public static class IntValue extends ConfigValue<Integer> {
      public IntValue(String key, String comment, Integer defaultValue) {
         super(key, comment, defaultValue);
      }
   }

   public static class DoubleValue extends ConfigValue<Double> {
      public DoubleValue(String key, String comment, Double defaultValue) {
         super(key, comment, defaultValue);
      }
   }

   public static final BooleanValue isSiegeModeEnabled = new BooleanValue("enablesiege", "Enable Enemies Swarming You", true);
   public static final BooleanValue Entity_Duplication = new BooleanValue("incenemies", "Enable Increased Enemy Presence", true);
   public static final BooleanValue isChargedCreeperAllowed = new BooleanValue("ischargedcreepers", "Charged Creepers Spawn Naturally", true);
   public static final DoubleValue ChargedCreeperChance = new DoubleValue("chargedchance", "Chance of Creepers Spawning Charged Out of 100", 0.1);
   public static final BooleanValue AngryEntities = new BooleanValue("isbloodlusting", "Allow Bloodlusting Enemies", true);
   public static final BooleanValue GiveZombiesPickaxe = new BooleanValue("iszombiepickaxes", "Give Zombies Pickaxes and Weapons?", true);
   public static final BooleanValue GiveZombiesArmor = new BooleanValue("iszombiearmor", "Give Zombies Armor?", true);
   public static final BooleanValue GiveSkeletonsArmor = new BooleanValue("isskeletonarmor", "Give Skeletons Armor?", true);
   public static final BooleanValue ZombiesLayTNT = new BooleanValue("iszombietnt", "Allow Zombies to Lay TNT?", true);
   public static final DoubleValue ZombieTNTChance = new DoubleValue("zombietntchance", "Chance of zombies placing TNT each tick if in distance. Default = 0.5", 0.5);
   public static final IntValue ZombieTNTDistance = new IntValue("zombietntdistance", "How close zombies need to be to player to allow setting TNT. Default=4 blocks.", 4);
   public static final BooleanValue AllowZombieGriefing = new BooleanValue("iszombiegriefing", "Allow Zombies To Destroy Blocks?", true);
   public static final BooleanValue AllowZombieGriefingOnlyIfPickaxe = new BooleanValue("iszombiedigifpickaxeonly", "If true, zombies can only destroy blocks if they have a pickaxe.", false);
   public static final BooleanValue AllowZombieBuilding = new BooleanValue("iszombiebuilding", "Allow Zombies To Place Blocks?", true);
   public static final DoubleValue ZombieDigHeightDistance = new DoubleValue("zombiedigheightdistance", "If the height distance (mob to player) less than this #, mob will start digging.", 16.0);
   public static final DoubleValue ZombieDigProximity = new DoubleValue("zombiedigproximity", "If entity is within circle defined by XZ proximity to target, start digging.", 8.0);
   public static final BooleanValue isCreeperBreachingAllowed = new BooleanValue("iscreeperbreaching", "Can Creepers Breach Walls?", true);
   public static final BooleanValue AllowSleeping = new BooleanValue("allowsleep", "Allow Sleeping?", false);
   public static final BooleanValue AllowMonsterInfighting = new BooleanValue("allowmonsterinfighting", "Allow Monster Infighting?", false);
   public static final BooleanValue ZombiesLightFires = new BooleanValue("zombieslightfires", "Allow Zombies and Piglins to light flammable things on fire?", true);
   public static final BooleanValue AllowSuperSkeletons = new BooleanValue("allowsuperskeletons", "Allow Super Skeletons to Spawn in the Game?", true);
   public static final BooleanValue isMonstersTargetVillagers = new BooleanValue("isMonstersTargetVillagers", "Should monsters target villagers?", true);
   public static final IntValue InvadeEveryXDays = new IntValue("siegerecurrence", "What days should sieges occur? 1 = default (everyday, constantly). 2 = every other day, 7 every week, and so on.", 1);
   public static final IntValue StartInvasionAfterXTicks = new IntValue("startinvasionafterxticks", "If number of in-game Ticks (Day is 24000 ticks) is greater or equal to this amount, start invasion.", 0);
   public static final IntValue DuplicationChance = new IntValue("dupechance", "Chance Entity Duplicate on Spawn (Out of 100)", 10);
   public static final IntValue AngryEntityChance = new IntValue("angryentitychance", "Chance of Entities Spawning Bloodlusted Out of 100", 10);
   public static final IntValue CreeperBreachingDistance = new IntValue("breachingdist", "Distance at Which Creepers Will Breach if Blocked (in Blocks)", 64);
   public static final IntValue CreeperStrikingDistance = new IntValue("creeperexplodedist", "Creepers Will Explode if This Close to Player (in Blocks)", 16);
   public static final IntValue CreeperAboveExplodeDistance = new IntValue("creepersbreechgrounddist", "Distance At Which Creepers Breech the Ground (in Blocks)", 6);
   public static final IntValue CreeperObstructedExplodeTicks = new IntValue("obstructedcreeperexpticks", "Ticks Until Blocked Creeper Explodes (in Game Ticks)", 8);
   public static final IntValue MobMaxLife = new IntValue("mobmaxlife", "Maximum ticks in an entity's life until they despawn (if their target is the player). 20 Ticks = 1 second.", 4800);
   public static final IntValue ChanceOfPickaxe = new IntValue("pickaxechance", "Chance of Zombies Receiving Special Item or Pickaxe Out of 100", 20);
   public static final DoubleValue ChanceOfZombieLightFire = new DoubleValue("zombiefirechance", "Chance of Zombies Lighting Flammable Items on Fire Per Tick Out of 100. Default = 4.0", 4.0);
   public static final DoubleValue ChanceOfSuperSkeleton = new DoubleValue("superskeletonchance", "Chance of More Powerful Skeleton Spawning", 1.0);
   public static final IntValue MonsterAttackDistanceAboveGround = new IntValue("grounddist", "Above Ground Monster Attack Distance (in Blocks)", 96);
   public static final IntValue MonsterAttackDistanceUnderground = new IntValue("cavedist", "Below Ground Monster Attack Distance (in Blocks)", 48);
   public static final BooleanValue SpidersShootWebs = new BooleanValue("isspidershootwebs", "Allow Spiders to Shoot Webs?", true);
   public static final DoubleValue SpiderShootWebChance = new DoubleValue("spiderwebchance", "Chance out of 100 for spider shooting webs each tick. Default = 1.0", 1.0);
   public static final IntValue SpiderShootWebDist = new IntValue("spiderwebdist", "Distance at which spider can shoot a web at player. Default = 2 blocks", 2);
   public static final IntValue MaxDuplicationClones = new IntValue("maxdupes", "Maximum Duplicated Enemies. Recommend Min. 1, Max. 4.", 3);
   public static final IntValue MinDuplicationClones = new IntValue("mindupes", "Minimum Duplicated Enemies. Recommend Min. 0, Max. 4.", 0);
   public static final IntValue MaxDungeonDuplicationClones = new IntValue("maxdungeondupes", "Maximum Duplicated Enemies FROM A DUNGEON. Recommend Min. 1, Max. 8.", 6);
   public static final IntValue MinDungeonDuplicationClones = new IntValue("mindungeondupes", "Minimum Duplicated Enemies FROM A DUNGEON. Recommend Min. 1, Max. 8.", 1);

   public static final ConfigValue<List<? extends String>> SiegeEntityWhitelist = new ConfigValue<>(
      "SiegeEntityWhitelist",
      "Entities that will besiege players.",
      List.of(
         "spider", "cave_spider", "creeper", "skeleton", "witch", "ghast", "giant", "husk", "hoglin",
         "pillager", "vindicator", "illusioner", "evoker", "ravager", "shulker", "silverfish", "stray",
         "slime", "zoglin", "piglin", "piglin_brute", "zombie", "zombie_villager", "zombified_piglin",
         "drowned", "wither_skeleton"
      )
   );

   public static final ConfigValue<List<? extends String>> BlocksEntitiesCannotDigThrough = new ConfigValue<>(
      "BlocksEntitiesCannotDigThrough",
      "Blocks that monsters will not be able to dig through",
      List.of(
         "coal", "iron", "diamond", "copper", "ore", "sign", "air", "water", "lava", "bed", "rail",
         "obsidian", "torch", "fire", "spawner", "ladder", "furnace", "level", "iron_door", "plate",
         "_button", "cane", "juke", "soul", "portal", "cake", "repeater", "trapdoor", "chain", "vine",
         "lily", "enchanting", "brewing", "cauldron", "end", "dragon", "potted", "skull", "head",
         "anvil", "chest", "dropper", "comparator", "hopper", "detector", "Pillar", "terracotta",
         "light", "prismarine", "book", "grass", "snow", "flower", "sapling", "bush", "stem",
         "carrots", "potatoes", "powered", "concrete", "barrier", "carpet", "banner", "beet",
         "void", "shulker", "structure", "compost", "piston", "dispenser", "fern", "pickle",
         "vhorus", "purpur", "beacon", "conduit", "frame", "lectern", "smith", "ancient", "armor",
         "wither", "barrel", "cutter", "hive", "lode", "respawn", "sculk", "brick"
      )
   );

   public static final ConfigValue<List<? extends String>> DuplicatedMobs = new ConfigValue<>(
      "DuplicatedMobs",
      "Mobs that can be duplicated (on spawn or through monster spawner)",
      List.of("creeper", "skeleton", "zombie", "drowned", "pillager", "spider", "zombified_piglin", "piglin", "piglin_brute", "stray")
   );

   public static final ConfigValue<List<? extends String>> FlammableBlocksWhitelist = new ConfigValue<>(
      "FlammableBlocksWhitelist",
      "Blocks that zombies will try to light on fire",
      List.of("plank", "stair", "fence", "door", "stripped")
   );

   public static void loadConfig() {
      try {
         Path configDir = FabricLoader.getInstance().getConfigDir();
         Path configFile = configDir.resolve("nightmareesm.json");
         if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
               JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
               for (ConfigValue<?> cv : ALL_CONFIGS) {
                  if (json.has(cv.getKey())) {
                     if (cv.getDefaultValue() instanceof Boolean) {
                        ((ConfigValue<Boolean>) cv).set(json.get(cv.getKey()).getAsBoolean());
                     } else if (cv.getDefaultValue() instanceof Integer) {
                        ((ConfigValue<Integer>) cv).set(json.get(cv.getKey()).getAsInt());
                     } else if (cv.getDefaultValue() instanceof Double) {
                        ((ConfigValue<Double>) cv).set(json.get(cv.getKey()).getAsDouble());
                     } else if (cv.getDefaultValue() instanceof List) {
                        List<String> list = new ArrayList<>();
                        json.get(cv.getKey()).getAsJsonArray().forEach(el -> list.add(el.getAsString()));
                        ((ConfigValue<List<String>>) cv).set(list);
                     }
                  }
               }
            }
            LOGGER.info("NightmareESM configuration loaded successfully.");
            saveConfig(configFile);
         } else {
            saveConfig(configFile);
         }
      } catch (Exception e) {
         LOGGER.error("Failed to load NightmareESM configuration", e);
      }
   }

   public static void saveConfig(Path configFile) {
      try {
         JsonObject json = new JsonObject();
         StringBuilder toml = new StringBuilder("# NightmareESM Configuration\n\n");
         for (ConfigValue<?> cv : ALL_CONFIGS) {
            if (cv.get() instanceof Boolean b) {
               json.addProperty(cv.getKey(), b);
               toml.append("# ").append(cv.comment).append("\n").append(cv.getKey()).append(" = ").append(b).append("\n\n");
            } else if (cv.get() instanceof Number n) {
               json.addProperty(cv.getKey(), n);
               toml.append("# ").append(cv.comment).append("\n").append(cv.getKey()).append(" = ").append(n).append("\n\n");
            } else if (cv.get() instanceof List<?> l) {
               json.add(cv.getKey(), GSON.toJsonTree(l));
               toml.append("# ").append(cv.comment).append("\n").append(cv.getKey()).append(" = ").append(GSON.toJson(l)).append("\n\n");
            }
         }
         Files.createDirectories(configFile.getParent());
         try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(json, writer);
         }
         Path tomlFile = configFile.getParent().resolve("nightmareesm-common.toml");
         try (Writer tomlWriter = Files.newBufferedWriter(tomlFile)) {
            tomlWriter.write(toml.toString());
         }
         LOGGER.info("NightmareESM configuration saved to {} and {}", configFile, tomlFile);
      } catch (Exception e) {
         LOGGER.error("Failed to save NightmareESM configuration", e);
      }
   }
}
