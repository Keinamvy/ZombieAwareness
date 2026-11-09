package com.corosus.zombieawareness.zday;

import com.corosus.zombieawareness.config.ZAConfigZDay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum ZDayStage {

    STAGE_1_OUTBREAK(1, "Quiet Outbreak", "Base blood scent & close-range sound investigation. Safe nights to scavenge and build."),
    STAGE_2_TACZ_ACOUSTIC(2, "Acoustic Discipline (TaCZ & Gunfire)", "Unsuppressed gunfire & explosions echo 112 blocks and build Chunk Heat. Reaching 100 Heat triggers a Horde Roar and Swarm Wave."),
    STAGE_3_BLACKOUT_STALKERS(3, "Blackout Stalkers", "Zombies hunt light sources from 64 blocks and smash perimeter torches, lanterns, and glowstone."),
    STAGE_4_BLOODMOON(4, "The Crimson Bloodmoon", "Periodic Bloodmoon nights with red sky, thick crimson fog, doubled hordes, and no sleeping."),
    STAGE_5_PICKAXE_BREACHERS(5, "Pickaxe Breachers", "Zombies spawn with pickaxes and mine through walls blocking players or loud sound/blood scent nodes."),
    STAGE_6_WEBS_AND_SPRINTERS(6, "Web-Trappers & Bloodlust Sprinters", "Spiders shoot cobweb projectiles to pin gunners; wounded/alerted zombies enter Bloodlust sprint frenzy."),
    STAGE_7_SCREAMER_SCOUTS(7, "Screamer Scouts & Swarm Overrun", "Screamer zombies unleash the Horde Roar on sight or gunfire, summoning 24+ zombie body-blocking waves."),
    STAGE_8_COMBAT_ENGINEERS(8, "Combat Engineers", "Zombies pillar vertically and build cobblestone bridges across trenches to reach rooftops."),
    STAGE_9_JUGGERNAUTS_AND_CRAWLERS(9, "Armored Juggernauts & Crawlers", "Heavy-armored Juggernauts soak rifle rounds in front; critically wounded zombies drop into fast ankle-biting Crawlers."),
    STAGE_10_TOTAL_COLLAPSE(10, "Demolition Sappers & Total Collapse", "Zombies deploy TNT and light fires, Creepers breach walls on blocked paths, and Bloodmoon swarms double.");

    private final int stageNumber;
    private final String title;
    private final String description;

    ZDayStage(int stageNumber, String title, String description) {
        this.stageNumber = stageNumber;
        this.title = title;
        this.description = description;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public int getUnlockDay() {
        return switch (this.stageNumber) {
            case 1 -> 1;
            case 2 -> ZAConfigZDay.dayStage2TaCZ;
            case 3 -> ZAConfigZDay.dayStage3Blackout;
            case 4 -> ZAConfigZDay.dayStage4Bloodmoon;
            case 5 -> ZAConfigZDay.dayStage5Breachers;
            case 6 -> ZAConfigZDay.dayStage6WebsAndSprinters;
            case 7 -> ZAConfigZDay.dayStage7Screamers;
            case 8 -> ZAConfigZDay.dayStage8Engineers;
            case 9 -> ZAConfigZDay.dayStage9Juggernauts;
            case 10 -> ZAConfigZDay.dayStage10Collapse;
            default -> 1;
        };
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public static ZDayStage getStageForDay(int worldDay) {
        ZDayStage highest = STAGE_1_OUTBREAK;
        for (ZDayStage stage : values()) {
            if (worldDay >= stage.getUnlockDay()) {
                if (stage.getStageNumber() > highest.getStageNumber()) {
                    highest = stage;
                }
            }
        }
        return highest;
    }

    public static List<ZDayStage> getUnlockedStagesBetween(int oldStageNum, int newStageNum) {
        List<ZDayStage> list = new ArrayList<>();
        for (ZDayStage stage : values()) {
            if (stage.stageNumber > oldStageNum && stage.stageNumber <= newStageNum) {
                list.add(stage);
            }
        }
        list.sort(Comparator.comparingInt(ZDayStage::getStageNumber));
        return list;
    }

    public static List<ZDayStage> getAllUnlockedStages(int currentStageNum) {
        List<ZDayStage> list = new ArrayList<>();
        for (ZDayStage stage : values()) {
            if (stage.stageNumber <= currentStageNum) {
                list.add(stage);
            }
        }
        list.sort(Comparator.comparingInt(ZDayStage::getStageNumber));
        return list;
    }

    public static ZDayStage getByNumber(int stageNumber) {
        for (ZDayStage stage : values()) {
            if (stage.stageNumber == stageNumber) {
                return stage;
            }
        }
        return STAGE_1_OUTBREAK;
    }
}
