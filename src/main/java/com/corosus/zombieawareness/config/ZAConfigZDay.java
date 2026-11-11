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
    public static int baseSwarmWaveSize = 28;

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
