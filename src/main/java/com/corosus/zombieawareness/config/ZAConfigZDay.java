package com.corosus.zombieawareness.config;

import com.corosus.modconfig.ConfigComment;
import com.corosus.modconfig.IConfigCategory;

import java.io.File;

public class ZAConfigZDay implements IConfigCategory {

    @ConfigComment("World day when Stage 2 (Acoustic Discipline & TaCZ Heat) unlocks")
    public static int dayStage2TaCZ = 4;

    @ConfigComment("World day when Stage 3 (Blackout Stalkers light hunting) unlocks")
    public static int dayStage3Blackout = 6;

    @ConfigComment("World day when Stage 4 (Bloodmoon periodic siege) unlocks")
    public static int dayStage4Bloodmoon = 8;

    @ConfigComment("World day when Stage 5 (Pickaxe Breachers) unlocks")
    public static int dayStage5Breachers = 11;

    @ConfigComment("World day when Stage 6 (Web-trappers & bloodlust sprinters) unlocks")
    public static int dayStage6WebsAndSprinters = 14;

    @ConfigComment("World day when Stage 7 (Screamer scouts & swarm overrun) unlocks")
    public static int dayStage7Screamers = 18;

    @ConfigComment("World day when Stage 8 (Combat Engineers bridge & pillar) unlocks")
    public static int dayStage8Engineers = 22;

    @ConfigComment("World day when Stage 9 (Armored Juggernauts & Crawlers) unlocks")
    public static int dayStage9Juggernauts = 26;

    @ConfigComment("World day when Stage 10 (Demolition Sappers & Total Collapse) unlocks")
    public static int dayStage10Collapse = 30;

    @ConfigComment("Frequency in days between Bloodmoons after Stage 4 is reached")
    public static int bloodmoonFrequencyDays = 6;

    @ConfigComment("Chunk heat threshold to trigger a Horde Roar and Swarm Wave")
    public static int chunkHeatThreshold = 100;

    @ConfigComment("Base number of zombies summoned in a gunfire swarm wave")
    public static int baseSwarmWaveSize = 48;

    @ConfigComment("Interval in ticks between rapid small-batch horde reinforcement pulses across all phases (15 ticks = 0.75s)")
    public static int hordeRapidSpawnIntervalTicks = 15;

    @ConfigComment("Base number of horde mobs spawned per rapid reinforcement pulse across all phases (small steady trickles instead of dozen+ bursts)")
    public static int hordeRapidSpawnBatchSize = 3;

    @ConfigComment("Maximum alive horde mobs allowed near an active horde center per solo player (scales up for squads and doubles during Bloodmoon)")
    public static int hordeMaxAliveNearPlayer = 180;

    @ConfigComment("Base main horde timer duration in ticks (6000 ticks = 5 minutes) before reinforcements stop and secondary cleanup begins")
    public static int hordeBaseDurationTicks = 6000;

    @ConfigComment("Secondary Rampage overtime duration in ticks (2400 ticks = 2 minutes) after the main horde timer expires if mobs remain")
    public static int hordeRampageDurationTicks = 2400;

    @ConfigComment("Arrow velocity multiplier applied to Rampage-phase horde skeletons (1.70 = 70% faster arrows)")
    public static double hordeRampageArrowSpeedMultiplier = 1.70;

    @ConfigComment("Bow attack interval in ticks for Rampage-phase horde skeletons (lower = faster fire rate)")
    public static int hordeRampageSkeletonAttackIntervalTicks = 8;

    @ConfigComment("Step-up height in blocks granted to Rampage-phase horde spiders (>= 1.05 enables instant 1-block step-up)")
    public static double hordeRampageSpiderStepHeight = 1.05;

    @ConfigComment("Chance (0.0 - 1.0) for a damaged hostile mob to spawn 2 clones of itself nearby")
    public static double mobDamageCloneChance = 0.08;

    @ConfigComment("Number of clones spawned when mobDamageCloneChance triggers")
    public static int mobDamageCloneCount = 2;

    @ConfigComment("Cooldown in ticks per mob before it can trigger damage-clone spawning again")
    public static int mobDamageCloneCooldownTicks = 200;

    @ConfigComment("Maximum nearby hostile mobs within 24 blocks before damage-clone spawning pauses")
    public static int mobDamageCloneMaxNearby = 48;

    @Override
    public String getName() {
        return "ZDayProgression";
    }

    @Override
    public String getRegistryName() {
        return "zdayprogression";
    }

    @Override
    public String getConfigFileName() {
        return "ZombieAwareness" + File.separator + getName();
    }

    @Override
    public String getCategory() {
        return "Zombie Awareness: Z-Day Progression";
    }

    @Override
    public void hookUpdatedValues() {
    }
}
