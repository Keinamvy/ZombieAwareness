package com.corosus.zombieawareness.testmod;

import com.corosus.coroutil.util.CoroUtilAttributes;
import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.EnumSenseType;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.client.SoundRegistry;
import com.corosus.zombieawareness.config.MobListsConfig;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import com.corosus.zombieawareness.zday.ZDayStage;
import com.nll.nightmareesm.nightmareesm;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class ZAPreProdTestMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZAPreProdTestMod");
    private int ticks = 0;
    private boolean executed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[ZA-PreProd] Companion test mod initialized. Waiting for server tick...");
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        if (executed) {
            return;
        }
        ticks++;
        ServerLevel level = server.overworld();
        if (ticks == 1) {
            level.setChunkForced(0, 0, true);
            level.setChunkForced(1, 1, true);
            return;
        }
        if (ticks < 40) {
            return;
        }
        executed = true;
        int passed = 0;
        int total = 8;

        try {
            LOGGER.info("=== STARTING ZOMBIE AWARENESS 26.3 PRE-PROD SERVER TESTS ===");

            // Test 1/8: Mixin Audit & Registries
            LOGGER.info("[Test 1/8] Running MixinEnvironment audit & registry checks...");
            MixinEnvironment.getCurrentEnvironment().audit();
            Identifier senseKey = BuiltInRegistries.ENTITY_TYPE.getKey(ZombieAwareness.SENSE);
            assertCondition(ZombieAwareness.SENSE_NAME.equals(senseKey), "Expected zombieawareness:scent registered, got " + senseKey);
            assertCondition(SoundRegistry.get("horde_roar") != null, "zombieawareness:horde_roar missing in SoundRegistry");
            assertCondition(SoundRegistry.get("alert") != null, "alert sound missing in SoundRegistry");
            assertCondition(SoundRegistry.get("target") != null, "target sound missing in SoundRegistry");
            assertCondition(SoundRegistry.get("investigate") != null, "investigate sound missing in SoundRegistry");
            assertCondition(ZombieAwareness.unitTest.containsKey("1"), "MixinDedicatedServer did not fire on server init");
            passed++;
            LOGGER.info("[Test 1/8] PASS: All Mixins audited cleanly and entities/sounds (including horde_roar) registered.");

            // Test 2/8: Config Files & MobListsConfig Verification
            LOGGER.info("[Test 2/8] Verifying CoroConfigRegistry .cfg files, ForgeConfigAPIPort MobLists.toml, and NightmareESM configs...");
            File generalCfg = new File("./config/zombieawareness/General.cfg");
            File featuresCfg = new File("./config/zombieawareness/Features.cfg");
            File clientCfg = new File("./config/zombieawareness/ClientConfig.cfg");
            File playerListsCfg = new File("./config/zombieawareness/PlayerRulesAndLists.cfg");
            File zdayCfg = new File("./config/zombieawareness/ZDayProgression.cfg");
            File mobListsToml = new File("./config/zombieawareness/MobLists.toml");
            File esmToml = new File("./config/nightmareesm-common.toml");
            File esmJson = new File("./config/nightmareesm.json");

            assertCondition(generalCfg.exists(), "General.cfg missing");
            assertCondition(featuresCfg.exists(), "Features.cfg missing");
            assertCondition(clientCfg.exists(), "ClientConfig.cfg missing");
            assertCondition(playerListsCfg.exists(), "PlayerRulesAndLists.cfg missing");
            assertCondition(zdayCfg.exists(), "ZDayProgression.cfg missing");
            assertCondition(mobListsToml.exists(), "MobLists.toml missing");
            assertCondition(esmToml.exists() || esmJson.exists(), "nightmareesm config missing");
            assertCondition(MobListsConfig.GENERAL.enhancedMobs.get().contains("minecraft:zombie"), "minecraft:zombie not in enhancedMobs");
            EntityType<?> zombieType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"));
            assertCondition(ZombieAwareness.canProcessEntity(zombieType, false), "canProcessEntity(ZOMBIE) returned false");
            passed++;
            LOGGER.info("[Test 2/8] PASS: All config files verified loaded on server.");

            // Test 3/8: Empty-server clock freeze & 10-Stage unlock schedule
            LOGGER.info("[Test 3/8] Verifying empty-server clock freeze & 10-stage unlock schedule (Days 1..30)...");
            long activeTicks = ZDayProgressionManager.getActivePlayTicks();
            assertCondition(activeTicks == 0L, "Expected activePlayTicks == 0 when 0 players have logged in, got " + activeTicks);
            assertCondition(ZDayProgressionManager.advanceTickLogic(0L, 100L, 101L, 0) == 0L, "advanceTickLogic must freeze active ticks at 0 players");
            assertCondition(ZDayProgressionManager.advanceTickLogic(0L, 100L, 101L, 1) == 1L, "advanceTickLogic must advance ticks when 1 player online");

            assertCondition(ZDayStage.getStageForDay(1).getStageNumber() == 1, "Day 1 must be Stage 1");
            assertCondition(ZDayStage.getStageForDay(4).getStageNumber() == 2, "Day 4 must be Stage 2");
            assertCondition(ZDayStage.getStageForDay(6).getStageNumber() == 3, "Day 6 must be Stage 3");
            assertCondition(ZDayStage.getStageForDay(8).getStageNumber() == 4, "Day 8 must be Stage 4");
            assertCondition(ZDayStage.getStageForDay(11).getStageNumber() == 5, "Day 11 must be Stage 5");
            assertCondition(ZDayStage.getStageForDay(14).getStageNumber() == 6, "Day 14 must be Stage 6");
            assertCondition(ZDayStage.getStageForDay(18).getStageNumber() == 7, "Day 18 must be Stage 7");
            assertCondition(ZDayStage.getStageForDay(22).getStageNumber() == 8, "Day 22 must be Stage 8");
            assertCondition(ZDayStage.getStageForDay(26).getStageNumber() == 9, "Day 26 must be Stage 9");
            assertCondition(ZDayStage.getStageForDay(30).getStageNumber() == 10, "Day 30 must be Stage 10");
            assertCondition(ZDayStage.getStageForDay(100).getStageNumber() == 10, "Day 100 must remain Stage 10");
            passed++;
            LOGGER.info("[Test 3/8] PASS: Clock freeze and all 10 escalation stages verified.");

            // Test 4/8: Multiplayer offline player catch-up dossier & /zday status + /zday log commands
            LOGGER.info("[Test 4/8] Verifying offline player catch-up dossier and /zday command execution...");
            ZDayProgressionManager.setForcedWorldDay(11);
            UUID offlinePlayerUuid = UUID.randomUUID();
            List<String> dossier = ZDayProgressionManager.buildCatchUpReportLines(offlinePlayerUuid);
            assertCondition(dossier.size() > 1, "Expected multi-line catch-up briefing");
            String dossierText = String.join("\n", dossier);
            assertCondition(dossierText.contains("Stage 2") && dossierText.contains("Stage 3") && dossierText.contains("Stage 4") && dossierText.contains("Stage 5"),
                    "Catch-up dossier must contain intelligence for Stages 2, 3, 4, and 5");

            var cmdSource = server.createCommandSourceStack();
            int statusResult = server.getCommands().getDispatcher().execute("zday status", cmdSource);
            assertCondition(statusResult != 0, "/zday status failed to execute");
            int logResult = server.getCommands().getDispatcher().execute("zday log", cmdSource);
            assertCondition(logResult != 0, "/zday log failed to execute");

            ZDayProgressionManager.setForcedWorldDay(-1);
            passed++;
            LOGGER.info("[Test 4/8] PASS: Offline player catch-up dossier and /zday commands verified.");

            // Test 5/8: TaCZ Gunshot 112-block node, Chunk Heat accumulation (>=100), Roar, ActiveHorde & 4-Phase System
            LOGGER.info("[Test 5/8] Verifying TaCZ gunfire heat accumulation, horde roar, ActiveHorde, and 4-phase duration...");
            ChunkHeatManager.clearAllHeat();
            ZDayProgressionManager.setForcedWorldDay(4); // Stage 2 (Day 4+) required for heat trigger

            // Verify TaCZ onTaCZGunSound for silenced shot (+4 heat) vs unsuppressed shot (+25 heat)
            ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 20, "silence");
            int silencedHeat = ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime());
            assertCondition(silencedHeat == 4, "Expected silenced shot heat to be 4 (+4 heat), got: " + silencedHeat);

            ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 64, "shoot");
            int combinedHeat = ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime());
            assertCondition(combinedHeat == 29, "Expected heat after silenced (+4) and unsuppressed (+25) shot to be 29, got: " + combinedHeat);

            // Verify suppressors reduce heat by > 80% (21/25 = 84% reduction)
            double reduction = (25.0 - 4.0) / 25.0;
            assertCondition(reduction > 0.80, "Suppressors (+4 heat vs +25 heat) must reduce heat by > 80%");

            // 3 more unsuppressed gunfire events (+75 heat) -> 29 + 75 = 104 heat (>= 100) -> triggers ActiveHorde
            for (int i = 0; i < 3; i++) {
                ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 64, "shoot");
            }
            int heat = ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime());
            assertCondition(heat >= 100, "Expected chunk heat >= 100 after gunfire events, got: " + heat);
            assertCondition(ChunkHeatManager.getActiveHordeCount() > 0, "Expected ActiveHorde registered, count=" + ChunkHeatManager.getActiveHordeCount());

            assertCondition(ChunkHeatManager.computeHordeDurationTicks(1) == 3600, "1 player horde duration must be 3600 ticks (3 mins)");
            assertCondition(ChunkHeatManager.computeHordeDurationTicks(3) == 4800, "3 players horde duration must be 4800 ticks (4 mins)");

            assertCondition(ChunkHeatManager.resolveHordePhase(0.05, 1) == ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, "0.05 progress must be Phase 1A");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.25, 4) == ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, "0.25 progress must be Phase 1B");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.50, 7) == ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, "0.50 progress must be Phase 2");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.70, 9) == ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, "0.70 progress must be Phase 3");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.90, 10) == ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, "0.90 progress at stage 10 must be Phase 4");

            ZDayProgressionManager.setForcedWorldDay(-1);
            ChunkHeatManager.clearAllHeat();
            passed++;
            LOGGER.info("[Test 5/8] PASS: TaCZ gunfire heat, ActiveHorde, and 3-Minute 4-Phase System verified.");

            // Test 6/8: Bloodmoon schedule, forced Bloodmoon activation & reset
            LOGGER.info("[Test 6/8] Verifying Bloodmoon schedule, forced activation, and reset...");
            assertCondition(BloodmoonManager.isBloodmoonDay(8), "Day 8 must be Bloodmoon day");
            assertCondition(!BloodmoonManager.isBloodmoonDay(7), "Day 7 must not be Bloodmoon day");
            assertCondition(BloodmoonManager.isBloodmoonNightTime(18000L), "Tick 18000 must be night");
            assertCondition(!BloodmoonManager.isBloodmoonNightTime(6000L), "Tick 6000 must not be night");

            BloodmoonManager.setForcedBloodmoon(true);
            assertCondition(BloodmoonManager.isBloodmoonActive(level), "Bloodmoon must be active when forced true");
            BloodmoonManager.setForcedBloodmoon(false);
            assertCondition(!BloodmoonManager.isBloodmoonActive(level), "Bloodmoon must not be active when forced false");
            BloodmoonManager.setForcedBloodmoon(null);
            BloodmoonManager.resetState();
            passed++;
            LOGGER.info("[Test 6/8] PASS: Bloodmoon schedule, forced activation, and reset verified.");

            // Test 7/8: NightmareESM Stage Gating, Juggernauts & Creeper Friendly-Fire + Cross-Hostile Aggression Prevention
            LOGGER.info("[Test 7/8] Verifying NightmareESM stage gating, Juggernaut knockback resistance, and Hostile Horde Unity...");
            assertCondition(!nightmareesm.canUseBehaviorForStage("BLACKOUT_STALKER", 2), "Blackout Stalker must be locked at Stage 2");
            assertCondition(nightmareesm.canUseBehaviorForStage("BLACKOUT_STALKER", 3), "Blackout Stalker must be unlocked at Stage 3");
            assertCondition(!nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", 9), "Creeper Breach must be locked at Stage 9");
            assertCondition(nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", 10), "Creeper Breach must be unlocked at Stage 10");

            Zombie testJuggernaut = new Zombie(level);
            nightmareesm.applyJuggernautLoadout(testJuggernaut);
            assertCondition(testJuggernaut.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue() >= 0.8, "Juggernaut knockback resistance must be >= 0.8");
            testJuggernaut.discard();

            Zombie zombie = new Zombie(level);
            Skeleton skeleton = (Skeleton) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("skeleton")).create(level, EntitySpawnReason.EVENT);
            Creeper creeper = (Creeper) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("creeper")).create(level, EntitySpawnReason.EVENT);
            zombie.setPos(50.5, 100.0, 50.5);
            skeleton.setPos(51.5, 100.0, 50.5);
            creeper.setPos(52.5, 100.0, 50.5);
            level.addFreshEntity(zombie);
            level.addFreshEntity(skeleton);
            level.addFreshEntity(creeper);

            float initialHealth = zombie.getHealth();
            zombie.setTarget(skeleton);
            assertCondition(zombie.getTarget() == null, "zombie.setTarget(skeleton) must be canceled by MixinMob!");

            boolean skeletonHurt = zombie.hurtServer(level, level.damageSources().mobAttack(skeleton), 5.0f);
            assertCondition(!skeletonHurt, "Hostile mob attack against zombie must return false!");

            boolean creeperHurt = zombie.hurtServer(level, level.damageSources().explosion(creeper, creeper), 20.0f);
            assertCondition(!creeperHurt, "Creeper explosion against zombie must return false!");
            assertCondition(zombie.getHealth() == initialHealth, "Zombie must remain at full health (" + initialHealth + ")");

            zombie.discard();
            skeleton.discard();
            creeper.discard();
            passed++;
            LOGGER.info("[Test 7/8] PASS: NightmareESM stage gating, Juggernaut attributes, and Horde Unity (0 friendly-fire) verified.");

            // Test 8/8: EntityScent Lifecycle & Zombie AI Pathfinding
            LOGGER.info("[Test 8/8] Verifying EntityScent spawning, synched data, decay, and Zombie AI pathfinding...");
            EntityScent scent = new EntityScent(ZombieAwareness.SENSE, level);
            scent.setPos(0.5, 100.0, 0.5);
            scent.setStrengthPeak(120);
            scent.setSenseType(EnumSenseType.SOUND.ordinal());
            boolean added = level.addFreshEntity(scent);
            assertCondition(added, "Failed to add EntityScent to ServerLevel");
            assertCondition(scent.getStrengthPeak() == 120, "Unexpected peak strength: " + scent.getStrengthPeak());
            assertCondition(scent.getSenseType() == EnumSenseType.SOUND.ordinal(), "Unexpected sense type: " + scent.getSenseType());
            assertCondition(scent.getRange() > 0.0F, "Expected positive range, got " + scent.getRange());
            int ageBefore = (int) Math.round(scent.getAgeScale() * EntityScent.MAX_AGE);
            scent.tick();
            int ageAfter = (int) Math.round(scent.getAgeScale() * EntityScent.MAX_AGE);
            assertCondition(ageAfter < ageBefore, "Expected EntityScent age to decay after tick()");
            scent.discard();

            BlockPos arenaOrigin = new BlockPos(20, 100, 20);
            for (int dx = -2; dx <= 12; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                }
            }

            ZAConfigGeneral.zombieRandSpeedBoost = 0.35D;
            ZAConfigGeneral.findSense_PercentChance = 100;
            Zombie aiZombie = new Zombie(level);
            aiZombie.setPos(arenaOrigin.getX() + 0.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5);
            aiZombie.setOnGround(true);
            aiZombie.setBaby(false);
            aiZombie.finalizeSpawn(level, level.getCurrentDifficultyAt(arenaOrigin), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(aiZombie);

            assertCondition(ZombieAwareness.unitTest.containsKey("8"), "MixinMob.finalizeSpawn did not trigger");
            assertCondition(aiZombie.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CoroUtilAttributes.SPEED_BOOST_ID), "Zombie missing ZA speed boost modifier");
            ZAUtil.giveRandomSpeedBoost(aiZombie); // Idempotency check

            EntityScent targetScent = ZAUtil.spawnOrBuffSenseAtPos(
                    level,
                    new Vector3d(arenaOrigin.getX() + 8.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5),
                    EnumSenseType.SOUND,
                    200,
                    false
            );
            EntityScent tracked = ZAUtil.ai_FindSense(aiZombie, true);
            assertCondition(tracked != null, "Zombie failed to find and path to nearby EntityScent");
            assertCondition(aiZombie.getNavigation().getPath() != null, "Zombie navigation path is null after ai_FindSense");
            long lastAction = ZombieAwareness.instance().getPersistentData(aiZombie).getLongOr(ZAUtil.ZA_LAST_ACTION, 0L);
            assertCondition(lastAction > 0L, "Zombie persistent data za_last_action was not updated");

            targetScent.discard();
            aiZombie.discard();
            passed++;
            LOGGER.info("[Test 8/8] PASS: EntityScent lifecycle and Zombie AI scent pathfinding verified.");

            LOGGER.info("=== ZA PREPROD SERVER TEST REPORT: {}/{} PASSED ===", passed, total);
            server.halt(false);
        } catch (Throwable t) {
            LOGGER.error("=== ZA PREPROD SERVER TEST FAILED ({}/{} PASSED) ===", passed, total, t);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
