# Unified Z-Day Apocalypse Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge `ZombieAwareness`, `NightmareESM`, a ported `Bloodmoon` visual/siege system (Red Moon/Sky, Red Fog, No-Sleep), and `TaCZ` gunfire/heat swarm mechanics into a single multiplayer-ready Fabric `26.3` mod with a 10-stage Active-Player World-Age Progression Engine and offline player catch-up logs.

**Architecture:** We partition the new subsystems into three strictly isolated packages (`com.corosus.zombieawareness.zday`, `com.corosus.zombieawareness.bloodmoon` + `heat`, and `com.nll.nightmareesm`) so three implementer subagents can build and unit-test Tasks 1, 2, and 3 in parallel without file conflicts. Task 4 integrates the hooks into `ZombieAwareness`, `ZAUtil`, `MixinMob`, and `zombieawareness.mixins_fabric.json`, then runs an 8-stage pre-prod verification suite on the real Fabric `26.3` dedicated server.

**Tech Stack:** Java 25 (`C:/Users/Keinamvy/Documents/jdk-25.0.5+8`), Gradle 9.9.0, Fabric Loom `1.19.4`, Minecraft `26.3` (official unobfuscated mappings), Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`, `ForgeConfigAPIPort` `v26.3.1`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-10-unified-zday-nightmare-design.md`

## Global Constraints

- **Orchestration & Execution Split**:
  - **Flash Subagents (`Model: "flash"`, Medium/High effort)** execute all code implementation and wiring tasks (Tasks 1, 2, 3 in parallel, followed by Task 4 Step 1 wiring & pre-prod test harness implementation).
  - **Main Agent (Coordinator)** personally performs the final verification phase (Task 4 Steps 2–5):
    1. Run `./gradlew.bat test build testmodJar --no-daemon` to verify all JUnit 5 unit tests (`CoroConfigRegistryTest`, `ZAUtilUnitTest`, `ZDayProgressionTest`, `ChunkHeatAndBloodmoonTest`, `NightmareStageGateTest`) and both output jars (`zombieawareness-fabric-26.3-1.13.1.jar` and `zombieawareness-testmod-26.3-1.13.1.jar`).
    2. Run **Graphify** across `src` (`graphify-out/graph.json`, `GRAPH_REPORT.md`, `graph.html`) and verify graph health (`0` import cycles, `0` dangling/missing edges, all communities connected cleanly) **before** running the final server pre-prod test.
    3. Stage and run the real Fabric `26.3` dedicated server (`fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar`) and verify all 8 Pre-Prod Server Test Stages pass (`=== ZA PREPROD SERVER TEST REPORT: 8/8 PASSED ===`) with exit code `0`.
    4. Prepare the git repository cleanly on branch `26.3-fabric` (no untracked scratch artifacts, clean `git status`, verified output JAR in `build/libs/zombieawareness-fabric-26.3-1.13.1.jar`) ready for GitHub pushing, and only then emit `<!-- GOAL_COMPLETE -->`.
- **Java & Toolchain**: Compile with JDK 25 (`org.gradle.java.home=C:/Users/Keinamvy/Documents/jdk-25.0.5+8`) and Minecraft `26.3` official unobfuscated mappings.
- **Zero Empty-Server Progression**: `ZDayProgressionManager` MUST NOT advance `activePlayTicks` when `playerCount == 0` unless an admin test override (`forcedWorldDay`) is explicitly set.
- **Non-Overlapping Parallel File Boundaries**: Tasks 1, 2, and 3 MUST ONLY create/modify the files listed in their respective `Files:` blocks so parallel subagents do not collide.
- **Production Jar Cleanliness**: Keep all pre-prod server verification code in `src/testmod` (`build/libs/zombieawareness-testmod-26.3-1.13.1.jar`) and unit tests in `src/test`.

## Review Focus

1. **Empty-Server Clock Freeze & Sleep-Skip Sync**: Verify `activePlayTicks` does not increment when `onlinePlayerCount == 0`, increments normally when `onlinePlayerCount > 0`, and advances to the next 24000-tick dawn boundary when online players sleep through a night.
2. **Offline Player Catch-Up Dossier (`ZDayPlayerLedger`)**: Verify a player who logs off at Stage 1 (Day 1) and reconnects at Stage 5 (Day 11) receives the exact delta of Stages 2, 3, 4, and 5 in their catch-up report and has their ledger updated to Stage 5.
3. **TaCZ Sound Classification & Localized Chunk Heat**: Verify `tacz:*` gunfire sounds (excluding quiet `inspect`/`reload`) add `+25` heat to the local `3x3` chunk cluster without raising heat in distant chunks, and trigger the Horde Roar + Swarm Wave at `>= 100` heat.
4. **Bloodmoon Schedule, Red Fog/Sky Client Mixins & No-Sleep Lock**: Verify Day 8 (`(worldDay - 8) % 6 == 0`) during night ticks `12000..23500` activates the Bloodmoon, blocks `ServerPlayer.startSleepInBed` with `BedSleepingProblem.NOT_SAFE` after saving respawn position, and applies crimson tint in `MixinFogRenderer` and `MixinSkyRenderer`.
5. **Sense-Then-Siege Stage Gating (`NightmareESM`)**: Verify `NightmareESM` behaviors respect the 10 stage unlock days (no Day 1 digging/TNT, Day 6 light-breaking, Day 11 pickaxe breaching toward players OR blocked sense nodes, Day 14 spider webs/bloodlust, Day 18 Screamer Scouts, Day 22 bridging/pillaring, Day 26 Juggernauts/Crawlers, Day 30 TNT/fires/Creeper wall-breaching).

---

### Task 1: Z-Day 10-Stage Progression Engine, Active-Player Clock, Offline Catch-Up Ledger & `/zday` Commands

**Files:**
- Create: `src/main/java/com/corosus/zombieawareness/config/ZAConfigZDay.java`
- Create: `src/main/java/com/corosus/zombieawareness/zday/ZDayStage.java`
- Create: `src/main/java/com/corosus/zombieawareness/zday/ZDayPlayerLedger.java`
- Create: `src/main/java/com/corosus/zombieawareness/zday/ZDayProgressionManager.java`
- Create: `src/main/java/com/corosus/zombieawareness/zday/ZDayCommands.java`
- Test: `src/test/java/com/corosus/zombieawareness/zday/ZDayProgressionTest.java`

**Interfaces:**
- Consumes: `com.corosus.modconfig.IConfigCategory`, `com.corosus.modconfig.ConfigComment`
- Produces:
  - `ZAConfigZDay`: static config fields (`dayStage2TaCZ = 4`, `dayStage3Blackout = 6`, `dayStage4Bloodmoon = 8`, `dayStage5Breachers = 11`, `dayStage6WebsAndSprinters = 14`, `dayStage7Screamers = 18`, `dayStage8Engineers = 22`, `dayStage9Juggernauts = 26`, `dayStage10Collapse = 30`, `bloodmoonFrequencyDays = 6`, `chunkHeatThreshold = 100`, `baseSwarmWaveSize = 16`)
  - `ZDayStage`: enum `STAGE_1_OUTBREAK(1, 1, "Quiet Outbreak", ...)` through `STAGE_10_COLLAPSE(10, 30, "Demolition Sappers & Total Collapse", ...)` with `static ZDayStage getStageForDay(int worldDay)` and `static List<ZDayStage> getUnlockedStagesBetween(int oldStageNum, int newStageNum)`
  - `ZDayProgressionManager`:
    - `public static void tickServer(MinecraftServer server)`
    - `public static long advanceTickLogic(long currentActiveTicks, long prevVanillaDayTime, long currentVanillaDayTime, int onlinePlayerCount)`
    - `public static int getActiveWorldDay()`
    - `public static ZDayStage getCurrentStage()`
    - `public static boolean isStageUnlocked(int stageNumber)`
    - `public static void setForcedWorldDay( int day)` (`-1` for auto)
    - `public static void onPlayerJoin(ServerPlayer player)`
  - `ZDayPlayerLedger`: JSON persistence (`load(Path)`, `save(Path)`, `recordPlayerSeen(UUID, int day, int stage)`, `getCatchUpStages(UUID, int currentStage)`)
  - `ZDayCommands`: `public static void register(CommandDispatcher<CommandSourceStack> dispatcher)`

- [ ] **Step 1: Write the failing unit test `src/test/java/com/corosus/zombieawareness/zday/ZDayProgressionTest.java`**
  Test:
  1. `emptyServerDoesNotAdvanceActivePlayTicks`: `advanceTickLogic(1000L, 1000L, 1001L, 0)` returns `1000L`.
  2. `populatedServerAdvancesActivePlayTicksAndHandlesSleepSkip`: `advanceTickLogic(13000L, 13000L, 24000L, 2)` advances by `11000L` to `24000L` (Day 2).
  3. `stageUnlockScheduleMatchesSpec`: Days `1, 4, 6, 8, 11, 14, 18, 22, 26, 30` map to Stages `1..10`.
  4. `offlinePlayerCatchUpReturnsMissedStagesAndPersists`: Player last seen at Stage 1 joining on Day 11 (Stage 5) receives Stages `2, 3, 4, 5` in `getCatchUpStages`, and subsequent call at Stage 5 returns empty list.

- [ ] **Step 2: Implement `ZAConfigZDay`, `ZDayStage`, `ZDayPlayerLedger`, `ZDayProgressionManager`, and `ZDayCommands`**
  Ensure `ZDayProgressionManager` tracks `activePlayTicks`, persists via `ZDayPlayerLedger` in `<world>/data/zombieawareness_zday_ledger.json`, announces dawn stage unlocks to online players, and sends the formatted "While You Were Away — Z-Day Apocalypse Briefing" on `onPlayerJoin(ServerPlayer)`.

- [ ] **Step 3: Run `./gradlew.bat test --tests com.corosus.zombieawareness.zday.ZDayProgressionTest --no-daemon` and verify PASS**

---

### Task 2: Bloodmoon Engine (Red Moon/Sky, Red Fog, No-Sleep Hook) & TaCZ Gunfire Chunk Heat + Horde Roar

**Files:**
- Create: `src/main/java/com/corosus/zombieawareness/bloodmoon/BloodmoonManager.java`
- Create: `src/main/java/com/corosus/zombieawareness/mixin/MixinServerPlayerSleep.java`
- Create: `src/main/java/com/corosus/zombieawareness/mixin/client/MixinFogRenderer.java`
- Create: `src/main/java/com/corosus/zombieawareness/mixin/client/MixinSkyRenderer.java`
- Create: `src/main/java/com/corosus/zombieawareness/heat/ChunkHeatManager.java`
- Test: `src/test/java/com/corosus/zombieawareness/heat/ChunkHeatAndBloodmoonTest.java`

**Interfaces:**
- Consumes: `ZDayProgressionManager.getActiveWorldDay()`, `ZDayProgressionManager.isStageUnlocked(int)`, `ZAConfigZDay`
- Produces:
  - `BloodmoonManager`:
    - `public static boolean isBloodmoonDay(int worldDay)` (true when `worldDay >= ZAConfigZDay.dayStage4Bloodmoon && (worldDay - ZAConfigZDay.dayStage4Bloodmoon) % ZAConfigZDay.bloodmoonFrequencyDays == 0`)
    - `public static boolean isBloodmoonNightTime(long dayTime)` (`long tod = dayTime % 24000L; return tod >= 12000L && tod <= 23500L;`)
    - `public static boolean isBloodmoonActive(Level level)` (checks forced override first, then `isBloodmoonDay(worldDay) && isBloodmoonNightTime(level.getDefaultClockTime())` where client uses `Math.max(ZDayProgressionManager.getActiveWorldDay(), (int)(level.getDefaultClockTime() / 24000L) + 1)`)
    - `public static void setForcedBloodmoon(Boolean forced)`
    - `public static void tickServer(ServerLevel overworld)` (triggers dusk `12000` Horde Roar & crimson broadcast and dawn `23500` survival message)
  - `ChunkHeatManager`:
    - `public static boolean isFirearmOrExplosionSound(String namespace, String path)` (matches `tacz` non-UI/non-reload sounds, and paths containing `gun`, `shoot`, `rifle`, `pistol`, `shotgun`, `sniper`, `smg`, `explode`, `explosion`)
    - `public static boolean isQuietFirearmHandlingSound(String namespace, String path)` (matches `tacz` reload/inspect/draw/holster)
    - `public static int addHeatAtBlockPos(int blockX, int blockZ, int amount, long gameTick)`
    - `public static int getHeatAtBlockPos(int blockX, int blockZ, long gameTick)`
    - `public static int computeSquadScaledWaveSize(int baseSize, int stageNum, int nearbyPlayers, boolean bloodmoon)`
    - `public static void onSoundPlayed(ServerLevel level, double x, double y, double z, String namespace, String path)` (adds heat at Stage 2+, triggers `playHordeRoar` and spawns `computeSquadScaledWaveSize` zombies 28–45 blocks away with a waypoint `EntityScent` when heat reaches `>= 100`)
    - `public static void playHordeRoar(ServerLevel level, double originX, double originY, double originZ)`

- [ ] **Step 1: Write the failing unit test `src/test/java/com/corosus/zombieawareness/heat/ChunkHeatAndBloodmoonTest.java`**
  Test:
  1. `taczSoundClassificationDistinguishesGunfireFromReload`: `isFirearmOrExplosionSound("tacz", "gun.shoot")` is `true`, `isFirearmOrExplosionSound("tacz", "gun.reload")` is `false`, `isQuietFirearmHandlingSound("tacz", "gun.reload")` is `true`.
  2. `chunkHeatAccumulatesLocallyAndDecaysWithoutAffectingDistantChunks`: adding heat at `(100, 100)` raises local `3x3` region heat to `>= 100` after 4 shots while `(2000, 2000)` remains `0`, and decays over time.
  3. `bloodmoonScheduleMatchesDay8AndEvery6DaysAtNight`: `isBloodmoonDay(7)` is `false`, `isBloodmoonDay(8)` is `true`, `isBloodmoonDay(14)` is `true`, `isBloodmoonNightTime(6000)` is `false`, `isBloodmoonNightTime(13000)` is `true`.
  4. `squadWaveScalingIncreasesWithPlayersAndBloodmoon`: verifies wave count scales up with squad size and doubles on Bloodmoon at Stage 10.

- [ ] **Step 2: Implement `BloodmoonManager`, `MixinServerPlayerSleep`, `MixinFogRenderer`, `MixinSkyRenderer`, and `ChunkHeatManager`**
  - In `MixinFogRenderer`, inject `@At("RETURN")` on `setupFog(Camera, int, DeltaTracker, float, ClientLevel)` returning `FogData`: when `BloodmoonManager.isBloodmoonActive(level)` is `true`, tint `fogData.color.set(0.48f, 0.03f, 0.03f, fogData.color.w)` and tighten `environmentalStart = Math.min(fogData.environmentalStart, 8.0f)` and `environmentalEnd = Math.min(fogData.environmentalEnd, 48.0f)`.
  - In `MixinSkyRenderer`, inject `@At("TAIL")` on `extractRenderState(ClientLevel, float, Camera, SkyRenderState)`: when `BloodmoonManager.isBloodmoonActive(level)` is `true`, set `skyRenderState.skyColor = new org.joml.Vector3f(0.38f, 0.02f, 0.02f)` and `skyRenderState.sunriseAndSunsetColor = new org.joml.Vector4f(0.70f, 0.04f, 0.04f, 0.75f)`.
  - In `MixinServerPlayerSleep`, inject `@At("HEAD")` cancellable on `ServerPlayer.startSleepInBed(AbstractBedBlock, BlockState, BedRule, BlockPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>>)`: when `BloodmoonManager.isBloodmoonActive(player.level())` is `true`, set respawn position (`player.setRespawnPosition(new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(player.level().dimension(), pos), player.getYRot(), player.getXRot()), false), true)`), send `"§4§l[Bloodmoon] The Crimson Moon rises — you cannot sleep tonight!"`, and return `Either.left(Player.BedSleepingProblem.NOT_SAFE)`.

- [ ] **Step 3: Run `./gradlew.bat test --tests com.corosus.zombieawareness.heat.ChunkHeatAndBloodmoonTest --no-daemon` and verify PASS**

---

### Task 3: Port & Upgrade `NightmareESM` Siege Behaviors with Stage Gating, Sense-Node Breaching, Blackout Stalkers, Screamers & Juggernauts

**Files:**
- Create: `src/main/java/com/nll/nightmareesm/BlockInfo.java`
- Create: `src/main/java/com/nll/nightmareesm/CannotReachPlayer.java`
- Create: `src/main/java/com/nll/nightmareesm/Clocker.java`
- Create: `src/main/java/com/nll/nightmareesm/DuplicateMob.java`
- Create: `src/main/java/com/nll/nightmareesm/ESMConfig.java`
- Create: `src/main/java/com/nll/nightmareesm/GetBlockingBlock.java`
- Create: `src/main/java/com/nll/nightmareesm/GetNearestTarget.java`
- Create: `src/main/java/com/nll/nightmareesm/GiveRandomArmor.java`
- Create: `src/main/java/com/nll/nightmareesm/GiveRandomPickaxe.java`
- Create: `src/main/java/com/nll/nightmareesm/MobInfo.java`
- Create: `src/main/java/com/nll/nightmareesm/PositionInfo.java`
- Create: `src/main/java/com/nll/nightmareesm/RNG.java`
- Create: `src/main/java/com/nll/nightmareesm/SetPlayerSpawn.java`
- Create: `src/main/java/com/nll/nightmareesm/SnapToBlockCenter.java`
- Create: `src/main/java/com/nll/nightmareesm/nightmareesm.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/GeneralEntity.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/NightmareCreeper.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/NightmareSkeleton.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/NightmareSpider.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/NightmareZombie.java`
- Create: `src/main/java/com/nll/nightmareesm/Base/SuperSkeleton.java`
- Create: `src/main/java/com/nll/nightmareesm/helpers/ConfigHelper.java`
- Create: `src/main/java/com/nll/nightmareesm/mixin/CreeperAccessor.java`
- Create: `src/main/java/com/nll/nightmareesm/mixin/FireBlockAccessor.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/CreeperBreachWalls.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/CreeperExplode.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/ItemChecker.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobBloodlust.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobBreakLightSources.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobBuildBridge.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobBuildUp.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobDamageBlock.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobDig.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobDigDown.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobDigUp.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobMaxLife.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobPlaceBlock.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobPlaceTNT.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobScreamerAlert.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobStartFires.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/MobTargetPlayer.java`
- Create: `src/main/java/com/nll/nightmareesm/MobBehavs/SpiderShootWeb.java`
- Test: `src/test/java/com/nll/nightmareesm/NightmareStageGateTest.java`

**Interfaces:**
- Produces:
  - `nightmareesm.init()` (loads `ESMConfig`)
  - `nightmareesm.LivingMobTick(Entity entity, int currentStage, boolean isBloodmoon)`
  - `nightmareesm.onFinalizeSpawn(Mob mob, EntitySpawnReason reason, int currentStage, boolean isBloodmoon)`
  - `nightmareesm.onMobDamaged(Mob mob, float damageAmount, int currentStage)` (handles Stage 9 Ankle-Biter Crawler transition when zombie drops below 25% HP)
  - `nightmareesm.canUseBehaviorForStage(String behaviorKey, int stageNumber)` (unit-testable gate helper verifying Stage 3 `BLACKOUT_STALKER`, Stage 5 `PICKAXE_DIG`, Stage 6 `SPIDER_WEB`/`BLOODLUST`, Stage 7 `SCREAMER_SCOUT`, Stage 8 `BRIDGE_PILLAR`, Stage 9 `JUGGERNAUT_CRAWLER`, Stage 10 `TNT_SAPPER`/`CREEPER_BREACH`/`START_FIRES`)
  - `MobBreakLightSources`: Stage 3+ behavior where zombies break adjacent torches, soul torches, wall torches, lanterns, and glowstone within 2 blocks when in dark areas.
  - `MobScreamerAlert`: Stage 7+ behavior for zombies tagged `"za_screamer"` — when targeting a player or near a fresh sound node, emits the Horde Roar and spawns a rapid sprint pack around the area (with per-screamer cooldown).
  - Sense-Node Digging in `MobDig` / `GetBlockingBlock`: works both when `mob.getTarget() != null` AND when `mob.getNavigation().getTargetPos() != null` (e.g. pathing toward a `ZombieAwareness` `EntityScent` sense node) and blocked by a wall (`CannotReachPlayer`).

- [ ] **Step 1: Write the failing unit test `src/test/java/com/nll/nightmareesm/NightmareStageGateTest.java`**
  Test that `nightmareesm.canUseBehaviorForStage(behaviorKey, stage)` strictly enforces the 10-stage unlock schedule (e.g., Stage 1 & 2 disallow all siege behaviors; Stage 3 enables `BLACKOUT_STALKER` only; Stage 5 enables `PICKAXE_DIG`; Stage 6 enables `SPIDER_WEB` and `BLOODLUST`; Stage 7 enables `SCREAMER_SCOUT`; Stage 8 enables `BRIDGE_PILLAR`; Stage 9 enables `JUGGERNAUT_CRAWLER`; Stage 10 enables `TNT_SAPPER`, `CREEPER_BREACH`, and `START_FIRES`).

- [ ] **Step 2: Copy `NightmareESM` classes from `C:\Users\Keinamvy\Downloads\NightmareESM\src\main\java\com\nll\nightmareesm` and upgrade them with stage gates, sense-node breaching, `MobBreakLightSources`, `MobScreamerAlert`, Juggernaut knockback/armor, and Crawler transition**
  - Ensure `MobTargetPlayer` only force-targets through walls when `isBloodmoon` is `true`; on normal nights, require `mob.hasLineOfSight(target)` or `mob.distanceTo(target) <= 10.0F` so `ZombieAwareness` stealth and scent/sound investigation work uninterrupted.

- [ ] **Step 3: Run `./gradlew.bat test --tests com.nll.nightmareesm.NightmareStageGateTest --no-daemon` and verify PASS**

---

### Task 4: Unified Integration & 8-Stage Real Fabric 26.3 Server Pre-Prod Verification

**Files:**
- Modify: `src/main/java/com/corosus/zombieawareness/ZombieAwareness.java`
- Modify: `src/main/java/com/corosus/zombieawareness/ZAUtil.java`
- Modify: `src/main/java/com/corosus/zombieawareness/loader/fabric/ZombieAwarenessFabric.java`
- Modify: `src/main/java/com/corosus/zombieawareness/mixin/MixinMob.java`
- Modify: `src/main/java/com/corosus/zombieawareness/mixin/MixinLivingEntity.java`
- Modify: `src/main/resources/zombieawareness.mixins_fabric.json`
- Modify: `src/testmod/java/com/corosus/zombieawareness/testmod/ZAPreProdTestMod.java`

- [ ] **Step 1 (Flash Subagent): Wire Tasks 1, 2, and 3 into `ZombieAwareness`, `ZAUtil`, `ZombieAwarenessFabric`, `MixinMob`, `MixinLivingEntity`, `zombieawareness.mixins_fabric.json`, and expand `ZAPreProdTestMod.java` to 8 Pre-Prod Server Test Stages**
  - Register `ZAConfigZDay` in `ZombieAwareness.init()` and call `nightmareesm.init()`.
  - Register server tick listener (`ZDayProgressionManager.tickServer` + `BloodmoonManager.tickServer`), player join listener (`ServerPlayConnectionEvents.JOIN` → `ZDayProgressionManager.onPlayerJoin`), and `/zday` command (`CommandRegistrationCallback.EVENT` → `ZDayCommands::register`) in `ZombieAwarenessFabric.onInitialize()`.
  - Hook `ChunkHeatManager.onSoundPlayed` in `ZAUtil.hookSoundEvent` so TaCZ gunfire and explosions spawn 112-block sense nodes and accumulate Chunk Heat at Stage 2+.
  - Upgrade `ChunkHeatManager.triggerSwarmWave` and `SoundRegistry` so:
    1. `SoundRegistry` registers `"horde_roar"` (`zombieawareness:horde_roar`) backed by custom `.ogg` files (`horde_roar_1.ogg`, `horde_roar_2.ogg`), and `ChunkHeatManager.playHordeRoar` plays `SoundRegistry.get("horde_roar")` alongside vanilla roar layers.
    2. `ChunkHeatManager.triggerSwarmWave` spawns a **Combined-Arms Mixed Horde** rather than only Zombies:
       - Stages 2–4: 85% Zombies / Husks + 15% Spiders
       - Stage 6+ (Day 14+): 65% Zombies (Frontline/Breachers) + 20% Web-Trapper Spiders (`SpiderShootWeb`) + 15% Suppression Skeletons (`NightmareSkeleton` / `SuperSkeleton`)
       - Stage 10+ (Day 30+): 55% Zombies (Juggernauts/Sappers) + 20% Spiders + 15% Skeletons + 10% Wall-Breaching Creepers (`NightmareCreeper` / `CreeperBreachWalls`)
       - Because Cross-Hostile Aggression & Creeper Friendly-Fire are disabled, Skeletons fire through the horde without triggering zombie infighting, Spiders web-pin the player, Creepers breach walls without killing the horde, and Zombies swarm in!
  - Hook `ZAUtil.spawnOrBuffSenseAtPos` in `MobDamageBlock` so breaching blocks emits noise that attracts nearby zombies to the breach point.
  - Hook `nightmareesm.LivingMobTick` and `nightmareesm.onFinalizeSpawn` in `MixinMob`, and `nightmareesm.onMobDamaged` in `MixinLivingEntity`.
  - **Enforce Hostile Horde Unity (No Creeper Friendly Fire & No Cross-Hostile Aggression)**:
    - Create helper `ZAUtil.isHostileMob(Entity entity)` (`return entity instanceof net.minecraft.world.entity.monster.Enemy || entity instanceof net.minecraft.world.entity.monster.Monster;`) and `ZAUtil.shouldBlockHostileFriendlyFireOrAggro(Entity attacker, Entity victim)` (`return isHostileMob(attacker) && isHostileMob(victim);`).
    - In `MixinLivingEntity`:
      - Inject `@At("HEAD")` cancellable on `hurtServer(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)`: if `(source.getEntity() instanceof Creeper || source.getDirectEntity() instanceof Creeper) && ZAUtil.isHostileMob((Entity)(Object)this)` OR `ZAUtil.shouldBlockHostileFriendlyFireOrAggro(source.getEntity(), (Entity)(Object)this)`, cancel and return `false` so creepers and skeleton arrows never damage or knock back fellow hostile mobs.
      - Inject `@At("HEAD")` cancellable on `setLastHurtByMob(LivingEntity attacker, CallbackInfo ci)`: if `ZAUtil.shouldBlockHostileFriendlyFireOrAggro(attacker, (Entity)(Object)this)`, cancel `ci.cancel()` so a Zombie hit by a Skeleton arrow never records the Skeleton as its revenge target.
    - In `MixinMob`:
      - Inject `@At("HEAD")` cancellable on `setTarget(LivingEntity target, CallbackInfo ci)`: if `target != null && ZAUtil.shouldBlockHostileFriendlyFireOrAggro((Entity)(Object)this, target)`, cancel `ci.cancel()` so hostile mobs never target each other.
    - Set `ESMConfig.AllowMonsterInfighting` default to `false`.
  - Add `MixinServerPlayerSleep`, `CreeperAccessor`, `FireBlockAccessor`, `MixinFogRenderer`, and `MixinSkyRenderer` to `zombieawareness.mixins_fabric.json`.
  - Expand `ZAPreProdTestMod.java` to test all 8 stages (including verifying in Stage 7/8 that a Creeper explosion damage source does `0` damage to a Zombie, and a Zombie hit by a Skeleton / `zombie.setTarget(skeleton)` refuses cross-hostile targeting):
    1. `[Test 1/8]` Mixin audit & registries (`MixinEnvironment.getCurrentEnvironment().audit()`)
    2. `[Test 2/8]` Config files (`General.cfg`, `ClientConfig.cfg`, `ZDayProgression.cfg`, `MobLists.toml`, `nightmareesm-common.toml`)
    3. `[Test 3/8]` Empty-server clock freeze (`activePlayTicks == 0` with 0 players) & 10-Stage unlock schedule (`Days 1..30`)
    4. `[Test 4/8]` Multiplayer offline player catch-up dossier (`ZDayPlayerLedger` & `ZDayProgressionManager.buildCatchUpReportLines`) & `/zday status` / `/zday log` command execution
    5. `[Test 5/8]` TaCZ gunshot (`tacz:gun_fire`) 112-block sense node, localized Chunk Heat accumulation (`>= 100`), directional Horde Roar, and Swarm Overrun wave spawn
    6. `[Test 6/8]` Bloodmoon schedule (Day 8 night), forced Bloodmoon activation, and No-Sleep bed check
    7. `[Test 7/8]` `NightmareESM` stage gating, Blackout Stalker torch breaking (`MobBreakLightSources`), Screamer Scout alert (`MobScreamerAlert`), and Juggernaut/Crawler attributes
    8. `[Test 8/8]` `EntityScent` lifecycle & `Zombie` AI pathfinding to `EntityScent`

- [ ] **Step 2 (Main Agent Verification): Run JUnit 5 Suite & Gradle Build**
  - Execute `.\gradlew.bat test build testmodJar --no-daemon` and fix any compilation/test issues until 100% green.

- [ ] **Step 3 (Main Agent Verification): Run Graphify Codebase Audit Before Final Server Testing**
  - Run the `graphify` pipeline on `src` to update `graphify-out/graph.json`, `graphify-out/GRAPH_REPORT.md`, and `graphify-out/graph.html`.
  - Verify graph health (`0` dangling/missing edges, `0` import cycles) and confirm all new subsystems (`ZDayProgressionManager`, `BloodmoonManager`, `ChunkHeatManager`, `nightmareesm`) are properly connected to `ZombieAwareness`, `ZAUtil`, and `MixinMob`.

- [ ] **Step 4 (Main Agent Verification): Execute Real Fabric 26.3 Server Pre-Prod Test Suite (8/8 Stages)**
  - Copy `build/libs/zombieawareness-fabric-26.3-1.13.1.jar` and `build/libs/zombieawareness-testmod-26.3-1.13.1.jar` to `scratch/preprod-server/mods/`.
  - Launch `fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar` under JDK 25 and confirm `=== ZA PREPROD SERVER TEST REPORT: 8/8 PASSED ===` with exit code `0`.

- [ ] **Step 5 (Main Agent Verification): Finalize Output JAR & Prepare Clean Git Repo for GitHub Push**
  - Verify `build/libs/zombieawareness-fabric-26.3-1.13.1.jar` contains zero `testmod` classes and zero bundled external mod jars.
  - Commit all changes cleanly on branch `26.3-fabric` and verify `git status` is 100% clean.
