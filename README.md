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
  - **Horde-Only 8% On-Damage Clones (`za_damage_clone`)**: Damaged **horde** hostile mobs (`isHordeMob`, never wild normal mobs) have an `8%` chance (`mobDamageCloneChance = 0.08`) to spawn `2` flanking clones (`mobDamageCloneCount = 2`), guarded by a `200`-tick per-mob cooldown, `48`-mob nearby cap, recursion prevention (`za_damage_clone` mobs never clone further), and **`25%` loot drop scaling** (`DAMAGE_CLONE_LOOT_MULTIPLIER = 0.25`). Likewise, NightmareESM spawn duplication only triggers on horde mobs.
- **Stage-Scaled Early-Game Elite Restraint (`computeStageEliteSpawnMultiplier`)**:
  - **Stage 1 (Days 1–20)**: `0.0x` elite multiplier — `0%` special units (standard Zombies & Spiders only).
  - **Stage 2 (Days 21–40)**: `0.15x` elite multiplier — rare Crawlers/Tankers only (~1–3%); `0%` Engineers, Knights, Screamers, Juggernauts, Sappers.
  - **Stages 3–4 (Days 41–56)**: `0.30x` / `0.40x` — modest Tankers/Crawlers and newly unlocked `[ENGINEER]`.
  - **Stages 5–6 (Days 57–72)**: `0.55x` / `0.70x` — unlocks `[KNIGHT]` at Stage 5 and `[SCREAMER]` at Stage 6 at reduced rates.
  - **Stages 7–8 (Days 73–90)**: `0.80x` / `0.90x` — unlocks `[JUGGERNAUT]` at Stage 7 and `[SAPPER]` at Stage 8; ambient NightmareESM Screamer & Juggernaut rolls also scale by stage.
  - **Stages 9–10 (Days 91–100+)**: `1.0x` full siege intensity.
- **70/30 Pincer & Silent Flank Waves**: `spawnPhasedSubWave` routes **70%** of spawns (`index % 10 < 7`) along the primary front (`approachAngleRad`, favoring `[TANKER]`, `[JUGGERNAUT]`, `[ENGINEER]`, and mainline troops) and **30%** into **silent flanking ambushes** (`index % 10 >= 7`, spawned `120°–240°` behind the front without an extra roar, favoring `[CRAWLER]`, `[KNIGHT]`, and `[SAPPER]`).
- **`<= 24m` Victory Close-Combat Retention**: When a horde ends in **Victory**, only stragglers further than **`24.0` blocks** from the nearest alive player retreat and despawn cleanly with smoke particles—mobs already engaged within **`<= 24.0` blocks** fight to the death! (All remaining Siege Engineer bridges/pillars collapse immediately on victory, wipe, or target loss.)
- **Active Player Tracking**: Hordes lock onto player positions (`noTargetTicks` auto-cancels after `100` ticks if no players remain in range) and **strictly ignore villagers** (`ZAUtil.shouldBlockHordeTarget` / `MixinMob`), keeping the siege focused on players and their defenses.

### The 6 Intra-Horde Phases (Extended Climax Phases 4 & 5)
Phases 4 and 5 are extended to occupy **55%** (`27% + 28%`) of the entire 5-minute siege so endgame demo crews and Bloodmoon Wardens have sustained pressure:

| Phase | Share / Window (5:00 Solo) | Codename | Threat & Spawns |
| :---: | :---: | :--- | :--- |
| **1A** | `0% – 10%` (`0:00 – 0:30`) | **The Cardio Check** | **Speed II Sprinter Zombies** backed by early **`[TANKER]` Zombies** soaking perimeter fire. |
| **1B** | `10% – 20%` (`0:30 – 1:00`) | **Spicy Cobwebs** | **Cave Spiders & Spiders**, Sprinters, and frontline **`[TANKER]`** units. |
| **2** | `20% – 32%` (`1:00 – 1:36`) | **Breachers & Cavalry** | **Pickaxe Breachers**, **Blackout Stalkers**, **Skeletons/Strays**, **`[TANKER]`**, **`[ENGINEER]`**, and mounted **`[KNIGHT]`** Zombie Horseman cavalry. |
| **3** | `32% – 45%` (`1:36 – 2:15`) | **Juggernauts & Crawlers** | Thrower **`[JUGGERNAUT]`**, **`[CRAWLER]`**, **`[ENGINEER]`**, **`[TANKER]`**, **`[KNIGHT]`**, Spiders, Skeletons, and Witches (plus aerial Phantom drops from Stage 5+). |
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
| **Siege Engineer** (`za_engineer`) | **`§3§l[ENGINEER]`** | `1.05x` scale | Spawns in Phase 2–5 front waves (~11–13%). Wields an Iron Pickaxe + Coarse Dirt offhand and Chainmail armor. Exclusively unlocks Cobblestone pillars (`MobBuildUp`) and Coarse Dirt bridges (`MobBuildBridge`) from **Stage 3+**. Placed blocks **auto-decay after `60s` (`1200` ticks)** with no drops and collapse immediately when the horde ends. |
| **Thrower Juggernaut** (`za_juggernaut`) | **`§4§l[JUGGERNAUT]`** | `1.20x` scale, `85%` KB resistance | Heavy iron/armored shock trooper. Every **`10s` (`200` ticks)** selects targets in **`8–28` blocks**—**strictly prioritizing enabled TACZ-Turrets before survival players**—and launches nearby `[CRAWLER]`/`[SAPPER]` units (or converts a nearby horde zombie into a `[CRAWLER]`) along a ballistic arc (`za_thrown_projectile`, fall-damage immune on landing). Thrown `[SAPPER]` units arm a **`2.5s` (`50`-tick)** landing fuse! |
| **Crawler** (`za_crawler`) | **`§e§l[CRAWLER]`** | `0.75x` scale, Speed II | Low-profile fast flanker inflicting **Slowness II** on hit; also hurled as Juggernaut siege ammo. |
| **Screamer Scout** (`za_screamer`) | **`§d§l[SCREAMER]`** | `1.05x` scale, **Glowing** | **Golden Helmet** scout that shrieks and summons reinforcements on contact. |
| **TNT Sapper** (`za_tnt_sapper`) | **`§c§l[SAPPER]`** | Demolitionist | Carries **TNT** in mainhand; plants `za_sapper_tnt` against fortified walls (0 friendly-fire to hostile/horde mobs), and detonates `2.5s` after touchdown when thrown by a Juggernaut. |
| **Apex Corrosive** (`za_apex_corrosive`) | **`§2§l[CORROSIVE]`** | Phase 5 modifier | Applies Necrotic Trauma (halved player healing for 8s), armor durability shred, and `40%` armor-bypass damage split. |

---

## ❤️‍🔥 1-Heart Clutch, 12s Adrenaline Surge & Elite Mineral Loot Drops

- **1-Heart Clutch Epilogue (`ZAUtil` + `MixinLivingEntity`, EnhancedVisuals-Compatible)**:
  - Whenever an alive non-creative/spectator `ServerPlayer` takes a non-void/non-kill hit that would drop them to **`<= 2.0F` HP (1 heart)** or kill them, there is a **`25%` chance** (`5`-minute / `6000`-tick cooldown) to cheat death cleanly at **`2.0F` HP (1 heart)**—directly activating **`EnhancedVisuals`**'s low-HP heartbeat & blurry vision overlay.
  - Grants **`2.0s` (`40` ticks)** of post-clutch damage immunity (`za$clutchInvulUntilTick`), plays `WARDEN_HEARTBEAT` + `TOTEM_USE`, flashes a randomized protagonist inner-monologue epilogue (actionbar + subtitle, e.g., *"Not here... not like this."*), and triggers **12 seconds (`240` ticks) of Adrenaline Surge**:
    - **Speed II**, **Haste II**, and **Strength I**.
    - **Life-on-Kill**: Every hostile or horde mob killed during Adrenaline Surge heals **`+2.0F` (+1 heart)** with crystal chime feedback.
- **Normal Horde Gunpowder Bonus & Elite Mineral Crafting Loot (`dropAllDeathLoot`)**:
  - Standard fodder `za_horde_mob` spawns drop vanilla loot **`20%`** of the time (80% suppression), PLUS receive a dedicated **`50%` chance to drop `1–3` Gunpowder** (`12.5%` for `za_damage_clone` normal horde mobs), bypassing the 80% fodder suppression so standard hordes reliably fuel gun/ammo crafting!
  - **`za_damage_clone`** mobs drop both vanilla loot and bonus elite mineral loot at **`25%` of a normal mob's rate**, and all **7 non-clone elite classes** (`za_screamer`, `za_tnt_sapper`, `za_engineer`, `za_tanker`, `za_knight`, `za_juggernaut`, `za_bloodmoon_warden`) bypass the suppression for full vanilla loot.
  - Non-clone elites (plus elite `za_damage_clone` kills on their `25%` roll) drop multi-bundle crafting supplies paired with **medium-tuned Diamond rates** and a **`<= 32` (half-stack) non-Warden Diamond cap per `6000`-tick (`5`-minute) rolling window** (`maxBonusDiamondsPerHordeWindow = 32`, overflow diamonds convert to `1–2` Gold Ingots, Iron Ingots, or Emeralds):
    - **Support** (`za_screamer`, `za_tnt_sapper`, `za_engineer`): `2–4` Iron Ingots, `2–4` Copper Ingots, `2–4` Gunpowder, `2–5` Coal, and `2–4` Redstone (`0%` Diamonds).
    - **Frontline** (`za_tanker`, `za_knight`): `4–7` Iron Ingots, `3–6` Copper Ingots, `2–4` Gold Ingots, `2–5` Gunpowder, `1–2` Emeralds (`60%`), and **`1` Diamond (`12%`)**.
    - **Heavy** (`za_juggernaut`): `6–10` Iron Ingots, `3–5` Gold Ingots, `4–8` Redstone, `1–3` Emeralds (`75%`), and **`1` Diamond (`25%`)**.
    - **Boss** (`za_bloodmoon_warden`): `1–2` Netherite Scrap, `2–4` Diamonds (`100%`), `2–4` Echo Shards, `4–6` Emeralds, and `6–10` Gunpowder.

---

## 🤝 Horde Unity: Zero Friendly-Fire

- 🚫 **Zero Cross-Hostile / Warden Aggro**: `ZAUtil.shouldBlockHostileFriendlyFireOrAggro`, `MixinLivingEntity`, `MixinMob`, and `MixinWarden#canTargetEntity` prevent hostile mobs and Bloodmoon Wardens from targeting or damaging each other (including `za_knight_mount` Zombie Horses, melee hits, and Sonic Boom blasts).
- 🛡️ **Explosion Immunity**: Creeper breaches and `za_sapper_tnt` detonations deal **0 damage** to fellow hostile or horde mobs.

---

## ⚡ Companion Addon Mod: `za-horde-optimizer`

Included directly in the repository under [`horde-optimizer/`](horde-optimizer/), **`za_horde_optimizer`** (`za-horde-optimizer-fabric-26.3-1.1.0.jar`) is a standalone Fabric 26.3 addon mod that eliminates mega-horde server lag:

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

## 📅 The 10-Stage 100-Day World-Age Escalation Table (Days 1 → 100+)

`ZDayProgressionManager` freezes world apocalypse progression whenever `0` players are online (`activePlayTicks` persisted in `<world>/data/zombieawareness_zday_ledger.json`) and presents returning players with a *"While You Were Away"* Intelligence Dossier. **Stages 1 & 2 cover the first 40 days** (`Days 1–20` and `Days 21–40`), ramping across `Days 41–100+`:

| Stage | Day Window | Codename | Key Unlock & Elite Scaling |
| :---: | :---: | :--- | :--- |
| **1** | **Days 1 – 20** | **Quiet Outbreak** | Base sensory AI & blood scent below 50% HP. Sleep permitted. `0%` horde elites. |
| **2** | **Days 21 – 40** | **TaCZ Acoustic Discipline** | Gunfire sound nodes (`112m`) & Chunk Heat (`100` threshold) trigger swarms. `0.15x` elite scale (rare Crawlers/Tankers only). |
| **3** | **Days 41 – 48** | **Blackout Stalkers & Siege Engineers** | Mobs smash perimeter torches/lanterns (`64m`); `[ENGINEER]` unlocks Stage 3+ 60s-decaying bridges/pillars (`0.30x` elites). |
| **4** | **Days 49 – 56** | **The Crimson Bloodmoon** | Periodic Bloodmoons (every 6 days), sleep blocked, doubled horde caps (`0.40x` elites). |
| **5** | **Days 57 – 64** | **Pickaxe Breachers & Knights** | Zombies mine walls/doors (`MobDig`), Phantoms begin aerial drops, and `[KNIGHT]` cavalry unlocks (`0.55x` elites). |
| **6** | **Days 65 – 72** | **Web-Trappers & Screamer Scouts** | Ranged web-shooting Spiders, Bloodlust sprints, and `[SCREAMER]` spawns (`0.70x` elites). |
| **7** | **Days 73 – 81** | **Juggernauts & Heavy Siege** | Thrower `[JUGGERNAUT]` unlocks (`0.80x` elites), ambient Screamers ramp up, and late-game hordes intensify. |
| **8** | **Days 82 – 90** | **Sapper Demolition** | `[SAPPER]` TNT demolitionists unlock (`0.90x` elites) alongside stray marksmen and sustained sieges. |
| **9** | **Days 91 – 99** | **Apex Escalation** | Full `1.0x` elite spawn rate + NightmareESM Juggernaut/Crawler damage conversions. |
| **10** | **Day 100+** | **Total Collapse** | Full Phase 4/5 `[CORROSIVE]` apex siege, Fire-Starters, Creeper breaching, and 3x Bloodmoon `[SIEGE WARDEN]` bosses. |

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
  - **`za-horde-optimizer`**: Drop `za-horde-optimizer-fabric-26.3-1.1.0.jar` alongside `zombieawareness-fabric-26.3-1.14.0.jar` for collision suppression + cluster optimization.

### Building, Automatic Versioning & Running Tests

- **Release Builds (`-Prelease=true` or `v*` Git tag)**: Produce clean SemVer artifacts (`26.3-1.14.0` and `26.3-1.1.0`), automated in [`.github/workflows/release.yml`](.github/workflows/release.yml).
- **Development Builds (default `./gradlew build` off-tag)**: Automatically append SemVer build metadata (`+build.<commitCount>.g<shortSha>`) for traceability, with optional `-Pmod_version=...` / `-Paddon_version=...` overrides.

```bash
# Ensure Java 25 is active
java -version

# Clean release build for both ZombieAwareness and za-horde-optimizer + run all JUnit 5 tests
./gradlew clean build -Prelease=true

# Non-release dev build (appends +build.<count>.g<sha>)
./gradlew build

# Also verify the Fabric testmod sources compile cleanly
./gradlew compileTestmodJava -Prelease=true
```

Built release artifacts are output to:
- Main mod: `build/libs/zombieawareness-fabric-26.3-1.14.0.jar`
- Horde Optimizer addon: `horde-optimizer/build/libs/za-horde-optimizer-fabric-26.3-1.1.0.jar`
