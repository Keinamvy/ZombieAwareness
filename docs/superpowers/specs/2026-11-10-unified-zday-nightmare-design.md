# Unified Z-Day Apocalypse Engine (`ZombieAwareness` + `NightmareESM` + `Bloodmoon` + `TaCZ`) — Design Specification

- **Date**: 2026-11-10
- **Target Platform**: Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`, Java `25`
- **Repository**: `C:\Users\Keinamvy\Downloads\ZombieAwareness` (branch `26.3-fabric`)
- **Source Codebases Merged**:
  1. **ZombieAwareness** (`com.corosus.zombieawareness.*` — sensory AI: blood scent, sound nodes, light investigation, wandering hordes)
  2. **NightmareESM** (`C:\Users\Keinamvy\Downloads\NightmareESM\src\main\java\com\nll\nightmareesm\**` — siege breaching: digging, bridging, pillaring, TNT sappers, creeper breaching, spider webs, bloodlust, armor/pickaxe loadouts, mob duplication)
  3. **Bloodmoon Visual & Siege System** (Crimson Sky & Horizon tint via `SkyRenderer`, thick Crimson Fog via `FogRenderer`, dusk/dawn omen broadcasts, no-sleep bed lock, and client-server payload sync)

---

## 1. Intent & Why

Players using firearm mods like **TaCZ (Timeless and Classics Zero)** and atmospheric shaders need a survival progression that:
1. **Gives new worlds breathing room (Days 1–3)** to gather resources, build a starter shelter, and craft early weapons without instant TNT/pickaxe griefing.
2. **Staggers new threats one by one as the world grows older** (`overworld.getDefaultClockTime() / 24000L`), so every few in-game days introduces a distinct tactical adaptation rather than dumping every mechanic on Day 1.
3. **Resolves the targeting conflict between `ZombieAwareness` and `NightmareESM`**:
   - Previously, `NightmareESM`'s `MobTargetPlayer` used unconditional X-ray distance targeting, which set `mob.getTarget() != null` permanently and bypassed `ZombieAwareness`'s sensory investigation (`ZAUtil.tickAI` only runs when `getTarget() == null`).
   - In the unified mod, **ZombieAwareness owns sensory detection and investigation** (sound, blood scent, light), while **NightmareESM owns breaching, vertical mobility, and siege combat** (digging toward sense nodes or targets, bridging, pillaring, webs, TNT, creeper wall-breaching).
4. **Balances TaCZ firearms through acoustic discipline and sheer swarm density**:
   - TaCZ bullets have limited entity penetration. Firing unsuppressed guns emits massive 96–128 block acoustic nodes and builds local **Chunk Heat**. Crossing the heat threshold triggers a terrifying **Horde Roar** followed by a high-count swarm wave (20–40+ zombies) that physically body-blocks bullets and punishes players who don't relocate.
5. **Supports Multiplayer natively**, including:
   - **Offline Catch-Up Intelligence Dossier**: Players who log in after time has passed on the server automatically receive a formatted chat summary of every mechanic unlocked while they were away, and can run `/zday` (or `/zday log`, `/zday status`) at any time.
   - **Per-Chunk / Per-Squad Heat & Scaling**: Gunfire heat is tracked per chunk/region so loud firefights at one base don't punish a quiet player 2,000 blocks away, and wave sizes scale with the number of nearby players within 64 blocks.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- Merge `NightmareESM` packages (`com.nll.nightmareesm.**` or unified under `com.corosus.zombieawareness.nightmare.**` / `com.nll.nightmareesm.**`) directly into the `ZombieAwareness` Fabric `26.3` build so a single mod jar delivers the complete Z-Day experience.
- Implement a **10-Stage World-Age Progression Engine** (`ZDayProgressionManager`) driven by Overworld day (`level.getDefaultClockTime() / 24000L`).
- Implement **TaCZ Acoustic & Light Integration** (`tacz:*` and firearm/explosion sound patterns in `ZAUtil` / `SoundRegistry`, plus localized Chunk Heat tracking).
- Implement the **Horde Roar** directional audio warning and **Swarm Overrun** wave spawner (`20–40+` zombies scaled by world stage and nearby squad size) when Chunk Heat overflows or a Screamer alerts.
- Port the **Bloodmoon System** (Server state + S2C network sync `BloodmoonSyncPayload` + Client `SkyRenderer` red sky/moon horizon tint + `FogRenderer` crimson fog + `ServerPlayer` no-sleep bed lock with spawn-point saving).
- Implement **Multiplayer Catch-Up & Status Commands** (`/zday`, `/zday log`, `/zday status`, plus automatic join catch-up report persisted per player UUID on the server).
- Verify all unit tests and a real Fabric `26.3` dedicated server pre-prod test suite covering progression stages, TaCZ sound/heat horde triggers, Bloodmoon no-sleep & state sync, NightmareESM breaching, and multiplayer catch-up logs.

### Non-Goals
- Modifying TaCZ's own jar or requiring TaCZ as a hard compile-time dependency (integration must work dynamically via sound event namespaces/paths and light checks so the mod runs both with and without TaCZ installed).
- Requiring players to install external `CoroUtil` or `NightmareESM` jars alongside this mod (only `fabric-api` and `forgeconfigapiport` remain external runtime dependencies).

### Anti-Goals (Failure Conditions Even If It Compiles)
- **No Day-1 Unfair Griefing**: Zombies must NEVER place TNT, light fires, or pickaxe through walls on Days 1–3.
- **No Unconditional X-Ray Wall-Hack on Normal Nights**: On non-Bloodmoon nights, mobs must NOT lock onto silent, dark, full-health players through solid walls from 64 blocks away (`MobTargetPlayer` only locks on if the mob has line-of-sight, is within close proximity, or is on an active Bloodmoon / high-heat siege wave).
- **No Global Punishment in Multiplayer**: One player shooting a rifle at `(1000, 1000)` must NOT spawn a horde on a stealthy player at `(-1000, -1000)`.

---

## 3. Staggered World-Age Unlock Schedule & Active-Player Clock

- **Active-Player Apocalypse Clock (`activePlayTicks`)**:
  - To prevent an empty multiplayer server (e.g., `pause-when-empty-seconds=0`) from advancing days while all players are offline, `ZDayProgressionManager` maintains `activePlayTicks` (persisted in `<world>/data/zombieawareness_zday_ledger.json`).
  - `activePlayTicks` **only increments on server ticks when `server.getPlayerCount() > 0`** (or when online players skip the night by sleeping on a non-Bloodmoon night, which advances `activePlayTicks` to the next dawn boundary).
  - Effective World Day is computed as `int worldDay = (int) (activePlayTicks / 24000L) + 1;` (so a brand-new world at `0` active ticks starts on **Day 1**). All day thresholds are configurable via `ZAConfigZDay.java` (registered in `CoroConfigRegistry`).

| Stage | Unlock Day | Mechanic Name | Unlocked Gameplay & AI Behavior |
| :---: | :---: | :--- | :--- |
| **1** | **Day 1** | **Quiet Outbreak** | Base `ZombieAwareness` scent (`SCENT_BLOOD` below 50% HP) and close-range sound investigation (`32` blocks). No speed boosts, no block digging, sleeping allowed every night. |
| **2** | **Day 4** | **Acoustic Discipline (TaCZ & Gunfire Hook)** | Any sound matching `tacz:*`, `*gun*`, `*shoot*`, `*rifle*`, `*pistol*`, `*shotgun*`, `*sniper*`, `*smg*`, or explosions generates a **112-block `EntityScent` sound node** and adds **Chunk Heat**. Crossing `100` Heat triggers a **Horde Roar** and spawns a **Swarm Wave** (`12 + 4 * squadSize` zombies) 28–45 blocks away heading toward the shooter. |
| **3** | **Day 6** | **Blackout Stalkers (Light Hunters)** | Zombies investigate light sources from `64` blocks (`ZAUtil.ai_FindLightSource`) and use `NightmareESM` block damage (`MobDamageBlock`) to smash exposed torches, soul torches, lanterns, and glowstone when navigating in the dark. |
| **4** | **Day 8** | **The Crimson Bloodmoon** | Unlocks periodic Bloodmoon nights (Day 8, and every `bloodmoonFrequencyDays = 6` days thereafter, plus configurable random chance). At dusk (`dayTime % 24000 == 12000`), a server-wide **Horde Roar** + crimson title warns players; client sky & fog turn deep crimson (`SkyRenderer` & `FogRenderer`); beds save respawn points (`SetPlayerSpawn`) but block sleeping (`BedSleepingProblem.NOT_SAFE`); horde spawn rates double until dawn (`23500`). |
| **5** | **Day 11** | **Pickaxe Breachers** | Zombies can spawn with wooden/stone/iron pickaxes (`GiveRandomPickaxe`) and use `MobDig`, `MobDigUp`, and `MobDigDown` to mine through walls when either targeting a player OR when blocked while pathing to a high-strength `EntityScent` node (`CannotReachPlayer`). Each mined block emits a sound node that attracts nearby zombies to the breach point. |
| **6** | **Day 14** | **Web-Trappers & Bloodlust Sprinters** | Spiders and Cave Spiders unlock `SpiderShootWeb` (pinning gunners in cobwebs); zombies gain increased `ZAUtil.giveRandomSpeedBoost` and enter `MobBloodlust` frenzy when wounded or near bleeding players. |
| **7** | **Day 18** | **Screamer Scouts & Overwhelming Swarms** | 8% of spawned zombies become **Screamer Scouts** (tagged via entity data/custom name visibility off, glowing eyes effect). When a Screamer spots a player or hears gunfire within 48 blocks, it unleashes the **Horde Roar** and summons an immediate **24-zombie body-blocking sprint wave** (`DuplicateMob` + pack spawn) to overwhelm low-penetration TaCZ magazines. |
| **8** | **Day 22** | **Combat Engineers (Bridge & Pillar)** | Zombies unlock `MobBuildUp` (vertical pillaring under themselves) and `MobBuildBridge` (horizontal cobble bridging across moats/trenches) when pathing to elevated players or rooftop snipers. |
| **9** | **Day 26** | **Armored Juggernauts & Ankle-Biter Crawlers** | 12% of zombies spawn as **Juggernauts** (`GiveRandomArmor` high-tier armor + `KNOCKBACK_RESISTANCE` + shield/pickaxe) to soak rifle rounds in front of the pack. Zombies reduced below 25% HP by non-lethal damage have a 25% chance to drop into fast low-profile **Crawlers** that inflict brief `SLOWNESS` on hit. |
| **10** | **Day 30+** | **Demolition Sappers & Total Collapse** | Zombies unlock `MobPlaceTNT` and `MobStartFires`; Creepers unlock `CreeperBreachWalls` (detonating against walls blocking their path to a player or high-priority scent/sound node) and higher charged-creeper rates (`NightmareCreeper`); Bloodmoon swarm wave sizes double (`30–50+` zombies per wave). |

---

## 4. Core Subsystem Designs

### 4.1 Sense-Then-Siege AI Bridge (`NightmareESM` ↔ `ZombieAwareness`)
- In `MobMixin` / `MixinMob`, `ZombieAwareness`'s `ZAUtil.tickAI(mob)` runs first.
- `NightmareESM`'s `LivingMobTick(mob)` is gated by `ZDayProgressionManager.getCurrentStage(level)`:
  - `MobTargetPlayer` no longer performs unconditional X-ray wall-hacks on normal nights. Instead, it only locks on if `mob.hasLineOfSight(player)` OR `mob.distanceTo(player) < 10.0` OR `BloodmoonManager.isBloodmoonActive(level)`.
  - **Digging/Breaching Toward Sense Nodes**: When `mob.getTarget() == null`, if the mob is actively pathing to an `EntityScent` node (`ZAUtil.getSenseNearEntity`) and `CannotReachPlayer` detects a blocked navigation path, `MobDig` / `MobDigUp` / `MobDigDown` (Stage 5+) may mine the blocking block between the mob and the `EntityScent` node.
  - Whenever `MobDamageBlock` breaks a block or `MobPlaceTNT` ignites TNT, it calls `ZAUtil.spawnOrBuffSenseAtPos` to create a high-strength sound node at the breach site, drawing nearby mobs into the breach funnel.

### 4.2 TaCZ Gunfire, Chunk Heat & The "Drop Everything & Run" Horde Roar
- **Acoustic Classification (`ZAUtil.isFirearmSound(Identifier soundId)`)**:
  - Matches any `Identifier` where `namespace.equals("tacz")` (excluding quiet UI/inspect sounds like `item.inspect` or `reload` which get medium range `28` blocks) or where `path` contains `"gun"`, `"shoot"`, `"fire"`, `"explosion"`, `"grenade"`, `"rifle"`, `"pistol"`, `"shotgun"`, `"sniper"`.
  - When triggered at Stage 2+ (Day 4+):
    1. Spawns an `EntityScent` sound node with `range = 112` blocks.
    2. Adds `+25` **Chunk Heat** to the shooter's `ChunkPos` region (`3x3` chunk cluster) in `ChunkHeatManager` (decays by `5` heat every second).
- **Horde Roar & Overwhelming Wave (`ChunkHeatManager.triggerHordeWave`)**:
  - When a `3x3` chunk cluster's heat reaches `100` (e.g., ~4 unsuppressed bursts or sustained fire, with a 60-second cooldown per region):
    1. **The Roar**: Plays a layered, directional horror roar (`SoundEvents.WARDEN_ROAR` at pitch `0.55f`, `SoundEvents.RAVAGER_ROAR` at pitch `0.65f`, and `SoundEvents.ZOMBIE_VILLAGER_CONVERTED` at pitch `0.5f`) originating from the chosen spawn vector 32–45 blocks from the player, accompanied by an action-bar warning: `"§4§l[!] A MASSIVE HORDE HAS HEARD YOUR GUNFIRE — RUN!"`.
    2. **The Overwhelming Wave**: Over the next 6 seconds (giving players a brief window to bolt), spawns `baseWaveSize * squadMultiplier` zombies (e.g., 16–40 zombies depending on World Day and nearby players within 64 blocks) on valid surface positions 28–45 blocks away, all given an initial `EntityScent` waypoint & long-distance path (`CoroUtilPath.tryMoveToXYZLongDist`) straight to the gunfire origin.

### 4.3 Ported Bloodmoon Visuals & No-Sleep Engine
- **Deterministic Shared Clock + Override Sync (`BloodmoonManager`)**:
  - Because Minecraft automatically synchronizes `level.getDefaultClockTime()` to all connected clients, `BloodmoonManager.isBloodmoonActive(Level level)` deterministically evaluates whether a night (`dayTime % 24000` between `12000` and `23500`) is a Bloodmoon night once World Day $\ge 8$ (Stage 4):
    - Deterministic schedule: Day 8, Day 14, Day 20, Day 26, Day 32... (`(worldDay - 8) % bloodmoonFrequencyDays == 0`).
  - At dusk (`12000`), broadcasts a crimson alert to all online players, plays the **Horde Roar**, and syncs any admin override (`BloodmoonSyncPayload`) via Fabric `ServerPlayNetworking`.
  - **No-Sleep Hook (`MixinServerPlayerSleep`)**: Injects into `ServerPlayer.startSleepInBed`; when `BloodmoonManager.isBloodmoonActive(level)` is `true`, saves the player's bed respawn position (`SetPlayerSpawn`) and cancels sleep with `Either.left(Player.BedSleepingProblem.NOT_SAFE)` and sends `"§c§lThe Bloodmoon rises... you cannot sleep tonight!"`.
- **Client Visuals (`MixinFogRenderer` & `MixinSkyRenderer`)**:
  - **`MixinFogRenderer`** (`net.minecraft.client.renderer.fog.FogRenderer#setupFog`): When `ClientBloodmoonState.isActive()` is `true` in the Overworld at night, interpolates `FogData.color` toward deep blood-crimson `(0.48f, 0.03f, 0.03f, 1.0f)` and pulls `FogData.environmentalStart` (`8.0f`), `FogData.environmentalEnd` (`42.0f`), and `FogData.renderDistanceEnd` closer so a thick crimson mist blankets the world.
  - **`MixinSkyRenderer`** (`net.minecraft.client.renderer.SkyRenderer#extractRenderState`): When `ClientBloodmoonState.isActive()` is `true`, tints `SkyRenderState.skyColor` (`(0.38f, 0.02f, 0.02f)`) and `SkyRenderState.sunriseAndSunsetColor` (`(0.70f, 0.04f, 0.04f, 0.75f)`) so the night sky and lunar halo glow crimson red.

### 4.4 Multiplayer Offline Catch-Up Ledger & `/zday` Command
- **Persistence (`ZDayPlayerLedger`)**:
  - Stores a JSON ledger at `<world_folder>/data/zombieawareness_zday_ledger.json` mapping each player's `UUID` to their `lastSeenWorldDay` and `lastSeenStage`.
- **On Player Join (`ServerPlayConnectionEvents.JOIN`)**:
  - Compares `playerLastSeenStage` with the server's `currentStage` (`1..10`) for `currentWorldDay`.
  - If `currentStage > playerLastSeenStage`:
    - Sends a formatted **"While You Were Away — Z-Day Apocalypse Briefing"** in chat listing every newly unlocked stage (`[Day X] Stage Name — What changed & how to survive it`).
    - Updates `lastSeenWorldDay` and `lastSeenStage` in the ledger and saves to disk.
  - If no new stages unlocked while offline, sends a concise 1-line status bar (`"§7[Z-Day] Day X (Stage Y/10) — Next escalation on Day Z. Type /zday for full dossier."`).
- **Real-Time Dawn Unlock Broadcast**:
  - When the server ticks across dawn (`dayTime % 24000 == 0`) into a milestone unlock day (`4, 6, 8, 11, 14, 18, 22, 26, 30`), all currently online players receive the unlock announcement immediately and have their ledger updated to `currentStage`.
- **Multiplayer Commands (`CommandRegistrationCallback`)**:
  - `/zday` or `/zday status`: Shows current World Day, current Stage (`X/10`), whether tonight is a Bloodmoon, current Chunk Heat at the player's position, and the next unlock day.
  - `/zday log`: Lists all unlocked stages from Day 1 up to the current World Day so players can review every active mechanic at any time.
  - `/zday teststage <day>` (OP permission level 2+): Allows server admins (and our pre-prod test harness) to jump/test specific world days or force-toggle a Bloodmoon (`/zday bloodmoon <true|false>`).

---

## 5. What Is Left to the Builder
- Exact package organization for the merged `NightmareESM` classes (keeping `com.nll.nightmareesm.**` in `src/main/java` so existing `NightmareESM` class references stay clean, wired into `ZombieAwarenessFabric` and `ZAUtil`).
- Exact tuning constants in `ZAConfigZDay.java` (defaulting to the table in Section 3, all overridable in `config/ZombieAwareness/ZDayProgression.cfg`).
