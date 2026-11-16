# 🧟‍♂️ Zombie Awareness: Z-Day Apocalypse Engine

> *"Congratulations on crafting your shiny new TaCZ assault rifle! You've got 30 rounds in the mag, a holographic sight, and a comforting sense of tactical superiority. Unfortunately, firing that unsuppressed burst just echoed across 112 blocks, spiked your local Chunk Heat past 100, and triggered a 5-minute siege with Netherite-clad Tankers soaking your turrets, spear-wielding Zombie Knights charging your flank, and—if the Bloodmoon rises—three Siege Wardens blasting your defense turrets from 48 blocks away."*

---

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-informational?style=flat-square&logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric-0.20.1+-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)
![Fabric API](https://img.shields.io/badge/Fabric--API-0.165.2+26.3-green?style=flat-square)
![ForgeConfigAPIPort](https://img.shields.io/badge/ForgeConfigAPIPort-v26.3.1+-purple?style=flat-square)

---

## 💀 What This Mod Actually Is

Vanilla Minecraft zombies are leisurely joggers with severe cataracts. You build a 3-block dirt pillar or hide behind a wooden door, and you're practically immortal.

**Zombie Awareness: Z-Day Apocalypse Engine** is an integrated survival overhaul running natively on **Fabric 26.3** and **Java 25**, combining five coordinated systems (plus an optional companion optimization addon mod):

1. 🩸 **Zombie Awareness (Sensory AI)**: Mobs navigate by sound, sight, and the iron tang of your blood. Bleeding below 50% HP leaves `SCENT_BLOOD` trails. Smashing blocks, sprinting, and gunfire spawn acoustic sense nodes tracked from across the biome.
2. ⛏️ **NightmareESM (Siege & Breaching)**: Pickaxe breachers mine through masonry, combat engineers pillar up walls and bridge across moats, web-trappers pin runners, and TNT sappers demolish bunkers.
3. 🌕 **Crimson Bloodmoon Engine**: Server-authoritative siege nights with crimson skies and fog, doubled horde caps, sleep lockdown, and Phase 5 **Siege Wardens** deployed against automated defenses.
4. 💥 **TaCZ & TACZ-Turrets Acoustic / Combat Integration**: Native, zero-hard-dependency integration. Gunfire builds **Chunk Heat**, while deployed **TACZ-Turrets** draw frontline `[TANKER]` aggro and face long-range Sonic Boom siege fire from Bloodmoon Wardens.
5. ⚡ **Horde Optimizer Addon (`za-horde-optimizer`)**: Included companion Fabric 26.3 mod (`horde-optimizer/`) eliminating $O(N^2)$ horde-to-horde collision/cramming and clustering dense hordes into $6\times 8\times 6$ leader-driven cells.

---

## 🔔 Gunfire Chunk Heat & The 5-Minute 6-Phase Horde Siege

In ordinary zombie shooters, guns solve problems. Here, guns solve *one* problem and kick off a 5-minute war of attrition.

```
+-------------------------------------------------------------------------------------+
|                                GUNFIRE ACOUSTIC LOOP                                |
|                                                                                     |
|   [Unsuppressed TaCZ Shot]  -->  112-Block Acoustic Node  -->  +25 Chunk Heat       |
|                                                                            |        |
|   [Heat Hits 100 Threshold] <--  Heat Accumulates Rapidly <----------------+        |
|               |                                                                     |
|               v                                                                     |
|   🔊 custom:horde_roar (128-block roar) + Crimson Alert Warning                     |
|               |                                                                     |
|               v                                                                     |
|   🚨 5-MINUTE (6000-TICK) 6-PHASE HORDE SIEGE (Rapid 15-Tick Small-Batch Spawns)    |
|               |                                                                     |
|               +--> [If survivors remain at 5:00] --> 🔥 2-MINUTE RAMPAGE OVERTIME   |
+-------------------------------------------------------------------------------------+
```

### Acoustic Echo, Horde Caps & Continuous Rapid Spawning
- Every unsuppressed TaCZ gunshot or explosion projects a **112-block acoustic node** (`EntityScent`) and adds **`+25` Chunk Heat** (`10` daytime / `25` night; suppressed shots add only `2` / `4` and clamp below the trigger threshold).
- **Base Duration**: **5 minutes (`6000` ticks)** via `ZAConfigZDay.hordeBaseDurationTicks`, extended by **+30 seconds (`+600` ticks)** per additional player in a 64-block squad radius.
- **Rapid Small-Batch Spawning**: Rather than dumping dozens of mobs in a single tick-spike, active hordes pulse every **`15` ticks (`0.75s`)** with **`3–6` mobs per pulse** (`hordeRapidSpawnIntervalTicks = 15`, `hordeRapidSpawnBatchSize = 3`, hard-capped well below a dozen per pulse), while respecting alive horde caps and unloaded-chunk guards.
- **Scaled Horde Caps & Attrition**:
  - `baseSwarmWaveSize = 48`
  - `hordeMaxAliveNearPlayer = 180` solo (scales `+40` per extra squadmate, and doubles to **`360+`** during Bloodmoon nights).
  - **8% On-Damage Clones (`za_damage_clone`)**: Damaged hostile mobs have an `8%` chance (`mobDamageCloneChance = 0.08`) to spawn `2` flanking clones (`mobDamageCloneCount = 2`), guarded by a `200`-tick per-mob cooldown, `48`-mob nearby cap, and recursion prevention (`za_damage_clone` mobs never clone further).
- **Active Player Tracking**: Hordes lock onto player positions (`noTargetTicks` auto-cancels after `100` ticks if no players remain in range) and **strictly ignore villagers** (`ZAUtil.shouldBlockHordeTarget` / `MixinMob`), keeping the siege focused on players and their defenses.

### The 6 Intra-Horde Phases (Extended Climax Phases 4 & 5)
Phases 4 and 5 are extended to occupy **55%** (`27% + 28%`) of the entire 5-minute siege so endgame demo crews and Bloodmoon Wardens have sustained pressure:

| Phase | Share / Window (5:00 Solo) | Codename | Threat & Spawns |
| :---: | :---: | :--- | :--- |
| **1A** | `0% – 10%` (`0:00 – 0:30`) | **The Cardio Check** | **Speed II Sprinter Zombies** backed by early **`[TANKER]` Zombies** soaking perimeter fire. |
| **1B** | `10% – 20%` (`0:30 – 1:00`) | **Spicy Cobwebs** | **Cave Spiders & Spiders**, Sprinters, and frontline **`[TANKER]`** units. |
| **2** | `20% – 32%` (`1:00 – 1:36`) | **Breachers & Cavalry** | **Pickaxe Breachers**, **Blackout Stalkers**, **Skeletons/Strays**, **`[TANKER]`**, and mounted **`[KNIGHT]`** Zombie Horseman cavalry. |
| **3** | `32% – 45%` (`1:36 – 2:15`) | **Juggernauts & Crawlers** | **`[JUGGERNAUT]`**, **`[CRAWLER]`**, **`[TANKER]`**, **`[KNIGHT]`**, Spiders, Skeletons, and Witches (plus aerial Phantom drops from Stage 5+). |
| **4** | `45% – 72%` (`2:15 – 3:36`) | **Climax Sappers (Extended 27%)** | Climax roar sounds. **`[SAPPER]`** TNT demolitionists, **`[JUGGERNAUT]`**, **`[TANKER]`**, **`[KNIGHT]`**, Creepers, Skeletons, and Phantom paratroopers. Decimation rule triggers early victory if `< 2%` remain. |
| **5** | `72% – 100%` (`3:36 – 5:00`) | **Apex Corrosion (Extended 28%)** | Armor-piercing & healing-reducing **`[CORROSIVE]`** apex horde (Wither Skeletons, Diamond-Spear Knights, Tankers, Sappers). On **Bloodmoon nights**, **3 `[SIEGE WARDEN]` bosses** emerge to snipe TACZ-Turrets from standoff range! |

*(Lower World Stages cap maximum unlocked phase: Stages 1–4 cap at Phase 1B, Stages 5–7 at Phase 2, Stages 8–9 at Phase 3, and Stage 10+ unlocks Phases 4 & 5.)*

---

## 🔥 2-Minute Rampage Overtime Phase (`za_rampage`)

When the 5-minute main horde timer expires (`elapsedTicks >= totalDurationTicks`):
- If **0** horde mobs remain nearby, victory triggers immediately.
- If **horde mobs still survive**, new reinforcement spawns **stop**, and the horde enters a **2-minute (`2400`-tick) Rampage Overtime** (`hordeRampageDurationTicks = 2400`) instead of despawning mobs mid-fight:
  - **All Survivors**: Gain persistent **Speed I** (`MobEffects.SPEED`, amplifier `0`, tagged `za_rampage`).
  - **Rampage Skeletons**: Arrow launch velocity boosted by **`1.70x`** (`MixinAbstractSkeleton`) with accelerated bow fire rate (`hordeRampageSkeletonAttackIntervalTicks = 8`, early release at `>= 10` draw ticks via `MixinRangedBowAttackGoal`).
  - **Rampage Spiders & Cave Spiders**: Gain **`Attributes.STEP_HEIGHT >= 1.05`** (`hordeRampageSpiderStepHeight = 1.05`) for instant 1-block step-up over low walls and trenches.
- Victory triggers the moment all enraged survivors are eliminated—or when the 2-minute Rampage timer completes (dispersing any stragglers at victory).

---

## 🛡️ Special Horde Units, Indicators & TACZ-Turrets Combat

All special horde units feature colored overhead nameplates (`setCustomNameVisible(true)`), `za_labeled_mob` exemption in `MixinMob.hookTick` so custom names never disable AI, scale adjustments, and unmistakable gear:

| Unit | Overhead Tag | Scale & Buffs | Signature Gear & Tactical Role |
| :--- | :---: | :--- | :--- |
| **Tanker Zombie** (`za_tanker`) | **`§b§l[TANKER]`** | `1.35x` scale, **`0.16`** speed (slower, skips random speed boost), `40+` HP, `90%` KB res, **Resistance III** | Netherite Helmet/Boots, Diamond Chest/Legs, Offhand **Shield**. Pulls enabled **TACZ-Turrets** (`tacz_turrets:turret` / `sentry_turret`) target focus every `5` ticks via `alertTo` + `ATTACK_TARGET`. |
| **Knight Zombie** (`za_knight` / `za_knight_mount`) | **`§6§l[KNIGHT]`** | `1.10x` scale, `30+` HP, `50%` KB res, **Resistance I** | Uses **vanilla MC 26.3 `ZombieHorse`** cavalry (`EntitySpawnReason.NATURAL`) carrying **Iron Spear** (or **Diamond Spear** in Phase 5) and full Iron armor; synchronized mount steering. |
| **Siege Warden** (`za_bloodmoon_warden`) | **`§5§l[SIEGE WARDEN]`** | `1.15x` scale, persistent (`setDigCooldown` kept topped up) | **3 spawn** once per Bloodmoon Phase 5 horde. Prioritizes enabled **TACZ-Turrets** within `64` blocks over players, holds a **`12–24` block standoff**, fires long-range **Sonic Boom** beams (`34`-tick charge, `14.0` sonic damage up to `48` blocks), and is strictly **blocked from targeting or damaging fellow horde/hostile mobs** (`MixinWarden` + `MixinLivingEntity`). |
| **Armored Juggernaut** (`za_juggernaut`) | **`§4§l[JUGGERNAUT]`** | `1.20x` scale, `85%` KB resistance | Heavy iron/random armor frontline shock trooper. |
| **Crawler** (`za_crawler`) | **`§e§l[CRAWLER]`** | `0.75x` scale, Speed II | Low-profile fast flanker inflicting **Slowness II** on hit. |
| **Screamer Scout** (`za_screamer`) | **`§d§l[SCREAMER]`** | `1.05x` scale, **Glowing** | **Golden Helmet** scout that shrieks and summons reinforcements on contact. |
| **TNT Sapper** (`za_tnt_sapper`) | **`§c§l[SAPPER]`** | Demolitionist | Carries **TNT** in mainhand; plants `za_sapper_tnt` against fortified walls (0 friendly-fire to hostile/horde mobs). |
| **Apex Corrosive** (`za_apex_corrosive`) | **`§2§l[CORROSIVE]`** | Phase 5 modifier | Applies Necrotic Trauma (halved player healing for 8s), armor durability shred, and `40%` armor-bypass damage split. |

---

## 🤝 Horde Unity: Zero Friendly-Fire

- 🚫 **Zero Cross-Hostile / Warden Aggro**: `ZAUtil.shouldBlockHostileFriendlyFireOrAggro`, `MixinLivingEntity`, `MixinMob`, and `MixinWarden#canTargetEntity` prevent hostile mobs and Bloodmoon Wardens from targeting or damaging each other (including `za_knight_mount` Zombie Horses, melee hits, and Sonic Boom blasts).
- 🛡️ **Explosion Immunity**: Creeper breaches and `za_sapper_tnt` detonations deal **0 damage** to fellow hostile or horde mobs.

---

## ⚡ Companion Addon Mod: `za-horde-optimizer`

Included directly in the repository under [`horde-optimizer/`](horde-optimizer/), **`za_horde_optimizer`** (`za-horde-optimizer-fabric-26.3-1.0.0.jar`) is a standalone Fabric 26.3 addon mod that eliminates mega-horde server lag:

1. **Horde Collision & Cramming Suppression**:
   - Intercepts `Entity#push`, `LivingEntity#pushEntities`, `LivingEntity#doPush`, and `LivingEntity#isPushable` (`MixinEntityCollision`, `MixinLivingEntityCollision`, `HordeCollisionPolicy`).
   - Skips $O(N^2)$ entity-push physics between horde mobs (preserving player/non-horde collisions and combat hits), recognizing all ZA tags (`za_horde_mob`, `za_tanker`, `za_knight`, `za_knight_mount`, `za_bloodmoon_warden`, `za_rampage`, etc. via `HordeMobDetector`).
2. **$6 \times 8 \times 6$ Spatial Cluster Optimization**:
   - Groups active horde mobs per `ServerLevel` into spatial grid cells (`SpatialClusterGrid`, `HordeClusterService`).
   - Runs expensive A* pathfinding, `PathNavigation` recomputation, `GoalSelector`, `Sensing`, and `ZAUtil.tickAI` primarily on each cluster's **leader** (`MixinMobAI`, `MixinPathNavigation`, `MixinZAUtilAI`), caching and cloning the leader's path/target for followers while keeping close-range (`<= 8` blocks) combat direct and exempting special autonomous units (`za_bloodmoon_warden`, `za_knight_mount`, riding `za_knight`).

---

## 🧵 Worldgen, Concurrency & Stability Hardening

Zombie Awareness and NightmareESM are hardened for heavily threaded modpacks (e.g., **C2ME**, **Lithium**, **TaCZ**):
- **C2ME / Off-Thread Deadlock Guard**: Worldgen spawns (`STRUCTURE`, `CHUNK_GENERATION`) and off-thread `WorldGenRegion` calls are gated out of `MixinMob#finalizeSpawn`, sound hooks (`MixinPlaySound`, `MixinLevelEvent`, `MixinNoteBlock`, `MixinExplosion`, `MixinTaCZSoundManager`), and `nightmareesm.onFinalizeSpawn`, with TaCZ gun events marshaled cleanly onto `server.execute(...)`.
- **Unloaded-Chunk Guards**: `CoroUtilPath`, `CoroUtilEntity`, `ChunkHeatManager`, and `NightmareESM` digging/building/bridging/fire/duplication routines validate `level.isLoaded(...)` / `hasChunk(...)` and sampled ray-cast chunks before touching block/heightmap state.
- **Thread-Safe State & Lifecycle Cleanup**: `ConcurrentHashMap` / `CopyOnWriteArrayList` collections, synchronized `SpatialClusterGrid` pruning without holding locks across callbacks, and full cleanup on `SERVER_STOPPED`, `ENTITY_UNLOAD`, and level unload.

---

## 📅 The 10-Stage World-Age Escalation Table (Days 1 → 30+)

`ZDayProgressionManager` freezes world apocalypse progression whenever `0` players are online (`activePlayTicks` persisted in `<world>/data/zombieawareness_zday_ledger.json`) and presents returning players with a *"While You Were Away"* Intelligence Dossier.

| Stage | Default Day | Codename | Key Unlock |
| :---: | :---: | :--- | :--- |
| **1** | **Day 1** | **Quiet Outbreak** | Base sensory AI & blood scent below 50% HP. Sleep permitted. |
| **2** | **Day 4** | **TaCZ Acoustic Discipline** | Gunfire sound nodes (`112m`) & Chunk Heat (`100` threshold) trigger directional swarms. |
| **3** | **Day 6** | **Blackout Stalkers** | Mobs hunt & smash torches/lanterns (`64m`). |
| **4** | **Day 8** | **The Crimson Bloodmoon** | Periodic Bloodmoons (every 6 days), sleep blocked, doubled horde caps. |
| **5** | **Day 11** | **Pickaxe Breachers** | Zombies mine walls/doors (`MobDig`); Phantoms begin aerial drops in hordes. |
| **6** | **Day 14** | **Web-Trappers & Bloodlust** | Ranged web-shooting Spiders & Bloodlust sprints. |
| **7** | **Day 18** | **Screamer Scouts** | Mutated `[SCREAMER]` scouts alert nearby hordes. |
| **8** | **Day 22** | **Combat Engineers** | Pillar-up (`MobBuildUp`) and chasm-bridging (`MobBuildBridge`). |
| **9** | **Day 26** | **Juggernauts & Crawlers** | `[JUGGERNAUT]` heavy shock troops & low-HP `[CRAWLER]` conversions. |
| **10** | **Day 30+** | **Demolition Sappers & Total Collapse** | `[SAPPER]` TNT breaching, Fire-Starters, Phase 4/5 `[CORROSIVE]` apex siege, and 3x Bloodmoon `[SIEGE WARDEN]` bosses. |

### Commands
- `/zday` or `/zday status`: Current World Day, Stage (`1–10`), Bloodmoon status, and Chunk Heat.
- `/zday log`: Lists all unlocked mechanics up to the current stage.
- `/zday teststage <day>` *(OP 2+)*: Jump directly to a day/stage for testing.
- `/zday bloodmoon <true|false|auto>` *(OP 2+)*: Force, clear, or restore automatic Bloodmoon scheduling.

---

## 🛠️ Installation, Configuration & Building From Source

### Requirements
- **Minecraft**: `26.3`
- **Fabric Loader**: `0.20.1+`
- **Fabric API**: `0.165.2+26.3`
- **ForgeConfigAPIPort**: `v26.3.1+`
- **Java**: `25`
- **Optional Integrations**:
  - **TaCZ (Timeless and Classics Zero)**: Firearm acoustic hooks & Chunk Heat.
  - **TACZ-Turrets (`tacz_turrets`)**: `[TANKER]` turret-focus pull and Bloodmoon `[SIEGE WARDEN]` anti-turret Sonic Boom prioritization.
  - **`za-horde-optimizer`**: Drop `za-horde-optimizer-fabric-26.3-1.0.0.jar` alongside `zombieawareness-fabric-26.3-1.13.1.jar` for collision suppression + cluster optimization.

### Building & Running Tests

```bash
# Ensure Java 25 is active
java -version

# Clean build both ZombieAwareness and za-horde-optimizer + run all JUnit 5 tests
./gradlew clean build

# Also verify the Fabric testmod sources compile cleanly
./gradlew compileTestmodJava
```

Built artifacts are output to:
- Main mod: `build/libs/zombieawareness-fabric-26.3-1.13.1.jar`
- Horde Optimizer addon: `horde-optimizer/build/libs/za-horde-optimizer-fabric-26.3-1.0.0.jar`
