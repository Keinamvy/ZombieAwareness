# 🧟‍♂️ Zombie Awareness: Z-Day Apocalypse Engine

> *"Congratulations on crafting your shiny new TaCZ assault rifle! You've got 30 rounds in the mag, a holographic sight, and a comforting sense of tactical superiority. Unfortunately, firing that unsuppressed burst just echoed across 112 blocks, spiked your local Chunk Heat past 100, and invited 40 screaming friends to your exact coordinates. Hope you remembered to practice your reload drills."*

---

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-informational?style=flat-square&logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric-0.20.1+-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)
![Fabric API](https://img.shields.io/badge/Fabric--API-0.165.2+26.3-green?style=flat-square)
![ForgeConfigAPIPort](https://img.shields.io/badge/ForgeConfigAPIPort-v26.3.1+-purple?style=flat-square)

---

## 💀 What This Mod Actually Is

Vanilla Minecraft zombies are leisurely joggers with severe cataracts. You build a 3-block dirt pillar or hide behind a wooden door, and you're practically immortal. 

**Zombie Awareness: Z-Day Apocalypse Engine** completely obliterates that complacency. It is an unholy, production-grade architectural fusion of four lethal survival systems running natively on **Fabric 26.3** and **Java 25**:

1. 🩸 **Zombie Awareness (Sensory AI)**: Mobs navigate by sound, sight, and the iron tang of your blood. Bleeding below 50% HP leaves glowing `SCENT_BLOOD` trails. Smashing blocks, sprinting, and gunshots spawn acoustic sense nodes that mobs track from across the biome.
2. ⛏️ **NightmareESM (Siege & Breaching)**: The days of hiding inside a cozy cobblestone bunker are dead. Hostile mobs form organized demolition crews—pickaxe breachers dig through masonry toward sound and blood scents, combat engineers pillar up walls and bridge across defensive moats, web-trappers pin runners, and TNT sappers breach hardened bunkers.
3. 🌕 **Crimson Bloodmoon Engine**: A server-authoritative siege event painted directly into the skybox and atmosphere. Red celestial horizons, crimson lunar halos, suffocating crimson fog, doubled horde spawn rates, and a complete lockdown on sleeping.
4. 💥 **TaCZ (Timeless and Classics Zero) Firearm Acoustics**: Native, zero-dependency acoustic integration. Every gunshot, shotgun blast, sniper crack, and grenade explosion acts as an auditory dinner bell with localized **Chunk Heat** tracking.

---

## 🔔 The "Gunfire Dinner Bell" & The 3-Minute 4-Phase Horde System

In ordinary zombie shooters, guns solve problems. Here, guns solve *one* problem and create thirty more.

```
+---------------------------------------------------------------------------------+
|                                GUNFIRE ACOUSTIC LOOP                            |
|                                                                                 |
|   [Unsuppressed TaCZ Shot]  -->  112-Block Acoustic Node  -->  +25 Chunk Heat   |
|                                                                        |        |
|   [Heat Hits 100 Threshold] <--  Heat Accumulates Rapidly <------------+        |
|               |                                                                 |
|               v                                                                 |
|   🔊 custom:horde_roar (128-block roar) + Crimson Alert Warning                |
|               |                                                                 |
|               v                                                                 |
|   🚨 3-MINUTE 4-PHASE HORDE SIEGE COMMENCES (Squad Scaled)                      |
+---------------------------------------------------------------------------------+
```

### Acoustic Echo & Chunk Heat
- Every unsuppressed TaCZ gunshot, rifle crack, shotgun blast, and explosion projects a massive **112-block acoustic node** through `EntityScent`.
- Each sustained burst adds **`+25` Chunk Heat** to a localized 3×3 chunk cluster.
- While heat naturally decays by `5` points every second during quiet periods, sustained firefights quickly trip the **`100` Heat Threshold**.

### The Roar & Squad Scaling
When Chunk Heat hits 100 (or when a Bloodmoon dusk arrives, or an alert Screamer spots you), the custom `zombieawareness:horde_roar` sound effect echoes up to **128 blocks** through the dark, underlaid with the visceral rumble of a Warden roar.

A **3-Minute Active Horde Event** (`180 seconds` / `3600 ticks`) locks onto your position.
- **Solo Defense**: Standard horde density.
- **Squad Play**: For every extra player holding the line within a 64-block squad radius, the siege is extended by **+30 seconds** (`+600 ticks`), and sub-wave mob quantities increase by **+50% per extra player** (`1.0 + 0.5 * (squad - 1)`).

### The 4 Intra-Horde Phases (Sub-waves every 15 seconds)

| Phase | Time Window | Codename | Incoming Nightmare Threat | Tactical Survival Advice |
| :---: | :---: | :--- | :--- | :--- |
| **1A** | `0:00 – 0:27` | **The Cardio Check** | **100% Fast Sprinter Zombies** sprint full-tilt into point-blank range with high-tier speed buffs. | Do not panic-dump your entire drum mag into the dirt. Hit your headshots and check your reload timers. |
| **1B** | `0:27 – 1:03` | **Spicy Cobwebs** | **Cave Spiders & Spiders** flank the sprinters. Cave Spiders inflict **Poison**; Spiders shoot high-velocity **Cobwebs** (`SpiderShootWeb`). | Poison drops your HP below 50%, spraying `SCENT_BLOOD` nodes everywhere. If a cobweb glues your boots, switch to secondary and spray. |
| **2** | `1:03 – 1:48` | **Home Renovation & Sniper Support** | **Pickaxe Breacher Zombies** (`MobDig`) systematically break down your walls while **Blackout Stalkers** smash all light sources. Backline **Suppression Skeletons & Strays** fire Slowness arrows over the crowd. | Your barricades are crumbling. Watch the sightlines—skeletons will pin you from 30 blocks while zombies chew through your drywall. |
| **3** | `1:48 – 2:33` | **Bullet Sponges & Ankle-Biters** | **Armored Juggernauts** with **85% Knockback Resistance** form a human shield. Wounded zombies turn into rapid **Ankle-Biter Crawlers**. **Combat Engineers** bridge/pillar to your roof, and **Witches** bombard your redoubt with debuff splash potions. | High-caliber rifle rounds will ricochet off Juggernaut plate. Aim low for the crawlers, watch your ceilings, and eliminate backline alchemists immediately. |
| **4** | `2:33 – 3:00+` | **The Eviction Notice** | A second **Climax Roar** sounds. **TNT Sappers** plant live explosives at your foundations, **Fire-Starters** ignite flammable cover, and **Wall-Breaching Creepers** blow structural entry holes. | Pack your bug-out bag. Your base is now an active demolition site. Fall back to your secondary fighting position or die in the rubble. |

*(Note: In younger worlds, earlier World Stages cap the maximum Horde Phase unlocked so new players aren't greeted with TNT sappers on Day 4.)*

---

## 🤝 Horde Unity: They Have Unionized Against You

In vanilla Minecraft, survivalists rely on standard cheese tactics: circle-strafe around a Skeleton so its stray arrows hit a Zombie, trigger an infighting brawl, grab some popcorn, and watch the mob AI kill itself.

**Not here.**

- 🚫 **Zero Cross-Hostile Aggression**: Hostile mobs (`Monster` / `Enemy`) never target or aggro onto each other. When a Stray puts an arrow into a Zombie's spine, the Zombie does not turn around. The horde shares a single collective objective: **you**.
- 🛡️ **Creeper Friendly-Fire Immunity**: Creeper detonations deal **0 damage to fellow hostile mobs**. When a Wall-Breaching Creeper detonates against your perimeter wall, the explosion shatters your reinforced stone and blows a 5-block breach hole—and the entire pack of 20 Sprinters and Juggernauts standing directly behind the blast walks through the smoke without taking a single hit point of damage.

---

## 📅 The 10-Stage World-Age Escalation Table (Days 1 → 30+)

The apocalypse doesn't drop a tactical nuke on your wooden shack on your very first night. Survival progression follows a calibrated, **10-Stage World-Age Escalation Engine** (`ZDayProgressionManager`). 

```
Day 1           Day 8             Day 14            Day 22            Day 30+
  |---------------|-----------------|-----------------|-----------------|
Quiet Outbreak  Crimson Bloodmoon  Webs & Bloodlust  Combat Engineers  Demolition Sappers
(Breathing Room) (Red Sky & Fog)  (Sticky Shoes)    (Pillars/Bridges) (Total Collapse)
```

| Stage | Unlock Day | Phase Codename | Tactical Threat & Behavior Changes |
| :---: | :---: | :--- | :--- |
| **1** | **Day 1** | **Quiet Outbreak** | Base sensory awareness. Zombies track blood scent (`SCENT_BLOOD`) if you fall below 50% HP, and investigate close-range sounds (`32` blocks). No block breaking, no speed boosts. Sleep is permitted every night. Enjoy the peace while it lasts. |
| **2** | **Day 4** | **TaCZ Acoustic Discipline** | Gunfire hook activated. Unsuppressed firearms and explosions generate **112-block sound nodes** and accumulate **Chunk Heat**. Crossing 100 Heat triggers a directional **Horde Roar** and a 12–24 mob swarm heading straight down your muzzle flash. |
| **3** | **Day 6** | **Blackout Stalkers** | Zombies actively hunt light sources from `64` blocks away. Exposed torches, lanterns, soul torches, and glowstone are targeted and smashed (`MobDamageBlock`), plunging your compound into pitch-black shader darkness. |
| **4** | **Day 8** | **The Crimson Bloodmoon** | Unlocks recurring Bloodmoon nights (Day 8, and every 6 days thereafter). At dusk, a server-wide roar sounds; the sky and lunar halo turn crimson; thick blood fog rolls in; beds save respawn points but sleep is strictly disabled (`NOT_SAFE`); horde spawn limits double. |
| **5** | **Day 11** | **Pickaxe Breachers** | Zombies spawn equipped with wooden, stone, or iron pickaxes. If their pathing toward a player or high-strength scent/sound node is blocked, they will systematically mine through walls, doors, and fortifications (`MobDig`). Each broken block creates more noise. |
| **6** | **Day 14** | **Web-Trappers & Bloodlust** | Spiders gain ranged web-slinging artillery (`SpiderShootWeb`), shooting cobwebs directly at fleeing gunners to pin them down. Zombies gain baseline sprint buffs and enter violent `MobBloodlust` frenzies when wounded or near blood scent. |
| **7** | **Day 18** | **Screamer Scouts** | 8% of zombies spawn as mutated **Screamer Scouts** (emissive glowing eyes). If a Screamer spots you or detects gunfire within 48 blocks, it unleashes a deafening roar and summons an immediate **24-zombie sprint wave** to overwhelm reload cycles. |
| **8** | **Day 22** | **Combat Engineers** | Zombies learn vertical and horizontal construction. They pillar upwards (`MobBuildUp`) to reach sniper perches, and build cobblestone bridges (`MobBuildBridge`) across moats, pits, and trenches to access your base. |
| **9** | **Day 26** | **Juggernauts & Crawlers** | Heavy shock-troops arrive. 12% of zombies spawn as **Armored Juggernauts** with heavy gear and **85% Knockback Resistance** to tank rifle magazines. Wounded zombies dropped below 25% HP have a 25% chance to drop into fast **Crawlers** that dodge headshots and inflict Slowness on hit. |
| **10** | **Day 30+** | **Demolition Sappers & Total Collapse** | The endgame siege. Zombies unlock **TNT Sappers** (`MobPlaceTNT`) and **Fire-Starters** (`MobStartFires`). Creepers trigger deliberate wall-breaches (`CreeperBreachWalls`) with complete hostile friendly-fire immunity. Bloodmoon waves escalate to 30–50+ mobs per wave. |

---

## 🌐 Multiplayer-First Features

### 1. Zero Empty-Server Clock Progression (`activePlayTicks`)
In standard modded Minecraft, hosting a dedicated server means that if nobody logs in from Monday to Thursday, the server ticks along, runs up 80 in-game days, and returning players find themselves on Day 90 facing TNT sappers with stone axes.
- **Our Fix**: The apocalypse clock tracks `activePlayTicks` (persisted in `<world>/data/zombieawareness_zday_ledger.json`).
- If **`0` players are online**, the apocalypse clock **freezes entirely**. Days only advance when real players are actively surviving on the server.

### 2. "While You Were Away" Intelligence Dossier
Went on vacation while your friends played through 15 in-game days? The moment you log back in, the server cross-references your UUID against the ledger:
```
=====================================================
 [!] WHILE YOU WERE AWAY — Z-DAY INTELLIGENCE DOSSIER
=====================================================
 You last logged in on Day 6 (Stage 3). 
 The server has advanced to Day 14 (Stage 6)!
 
 [UNLOCKED] Stage 4 (Day 8): The Crimson Bloodmoon
   -> Bloodmoon nights now occur periodically. Sleep is disabled!
 [UNLOCKED] Stage 5 (Day 11): Pickaxe Breachers
   -> Zombies now spawn with pickaxes and mine through walls!
 [UNLOCKED] Stage 6 (Day 14): Web-Trappers & Bloodlust
   -> Spiders shoot cobwebs; zombies sprint with bloodlust!
=====================================================
 Type /zday for current tactical status.
```

### 3. In-Game Commands
- `/zday` or `/zday status`: Displays the current World Day, active Stage (e.g. `Stage 6/10`), Bloodmoon forecast, and current Chunk Heat at your coordinates.
- `/zday log`: Lists every unlocked mechanic from Day 1 to your current day for a complete tactical review.
- `/zday teststage <day>` *(OP level 2+)*: Jump to any specific day for server testing or scenario design.
- `/zday bloodmoon <true|false|auto>` *(OP level 2+)*: Manually trigger, cancel, or restore automated scheduling for the Crimson Bloodmoon.

---

## 🎥 Tactical Reconnaissance (Showcase)

| Light Awareness & Torches | Mining & Blood Scent Tracking | TNT Sapper Demolition |
| :---: | :---: | :---: |
| ![Light Investigation](showcase/light_awareness.gif) | ![Mining toward scent](showcase/mining_sound_blood_smell.gif) | ![TNT Demolition](showcase/tnt_sound.gif) |
| *Blackout Stalkers targeting torches in darkness* | *Zombies mining through walls toward blood scent* | *Demolition Sappers breaching barricades with TNT* |

---

## 🛠️ Installation & Requirements

### System & Mod Requirements
- **Minecraft**: `26.3`
- **Fabric Loader**: `0.20.1+`
- **Fabric API**: `0.165.2+26.3`
- **ForgeConfigAPIPort**: `v26.3.1+`
- **Java**: `25` (Temurin / OpenJDK 25 recommended)
- *(Optional, Recommended)*: **TaCZ (Timeless and Classics Zero)** for firearms, and your favorite Fabric shader pack for atmospheric Bloodmoons.

### Building & Testing From Source
If you are compiling or contributing to the codebase, ensure your `JAVA_HOME` points to Java 25:

```bash
# Verify Java version (must be 25+)
java -version

# Run the automated test suite (stages, heat, and multiplayer tests)
./gradlew test

# Compile production jar
./gradlew build

# Package testmod jar for development verification
./gradlew testmodJar
```

---

## 💡 Quick Survival Guide for Beginners

1. **Days 1–3 are for hoarding**: Strip-mine, gather iron, craft armor, and reinforce your base. Do not waste this grace period building aesthetic glass balconies.
2. **Suppressors are not optional**: If you play with TaCZ, craft a silencer immediately. Firing unsuppressed rifles inside your main living room is a death sentence.
3. **Keep bandages on your hotbar**: When you drop below 50% HP, you bleed. Bleeding zombies will track you through caves, tunnels, and walls. Heal before you run.
4. **Double-layer your walls**: Stone and deepslate take longer to dig than dirt or wood. When Pickaxe Breachers show up on Day 11, thin single-block walls will give you about 4 seconds of warning before they're inside.
5. **Prepare a Bloodmoon bunker**: When dusk on Day 8 turns blood red and the horn sounds, retreat to a fortified position with clear sightlines and blast doors. You cannot sleep your problems away anymore.
