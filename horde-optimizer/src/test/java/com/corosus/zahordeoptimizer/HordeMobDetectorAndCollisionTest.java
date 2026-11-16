package com.corosus.zahordeoptimizer;

import com.corosus.zahordeoptimizer.collision.HordeCollisionPolicy;
import com.corosus.zahordeoptimizer.config.HordeOptimizerConfig;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HordeMobDetectorAndCollisionTest {

    @BeforeEach
    void setUp() {
        HordeOptimizerConfig.resetDefaults();
        ChunkHeatManager.clearAllHeat();
    }

    @AfterEach
    void tearDown() {
        HordeOptimizerConfig.resetDefaults();
        ChunkHeatManager.clearAllHeat();
    }

    @Test
    void detectsAllCanonicalZombieAwarenessHordeTags() {
        for (String tag : HordeMobDetector.HORDE_TAGS) {
            assertTrue(HordeMobDetector.hasHordeTag(Set.of(tag)),
                    "Expected tag '" + tag + "' to be recognized as a horde mob tag");
        }
        assertTrue(HordeMobDetector.hasHordeTag(Set.of("za_tanker")), "za_tanker must be recognized");
        assertTrue(HordeMobDetector.hasHordeTag(Set.of("za_bloodmoon_warden")), "za_bloodmoon_warden must be recognized");
        assertTrue(HordeMobDetector.hasHordeTag(Set.of("za_knight")), "za_knight must be recognized");
        assertTrue(HordeMobDetector.hasHordeTag(Set.of("za_knight_mount")), "za_knight_mount must be recognized");
        assertTrue(HordeMobDetector.hasHordeTag(Set.of("za_rampage")), "za_rampage must be recognized");
        assertFalse(HordeMobDetector.hasHordeTag(Collections.emptySet()));
        assertFalse(HordeMobDetector.hasHordeTag(null));
        assertFalse(HordeMobDetector.hasHordeTag(List.of("ambient_mob", "pet_wolf")));
    }

    @Test
    void detectsActiveHordeRadiusCorrectly() {
        BlockPos center = new BlockPos(100, 64, 100);
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(center, Collections.emptySet(), 1, 10, 5));

        assertTrue(HordeMobDetector.isWithinActiveHordeRadius(120.0, 64.0, 100.0));
        assertFalse(HordeMobDetector.isWithinActiveHordeRadius(300.0, 64.0, 300.0));
    }

    @Test
    void suppressesHordeToHordePushWhilePreservingNonHordeAndPlayerCollisions() {
        // Horde <-> Horde: suppressed
        assertTrue(HordeCollisionPolicy.shouldSuppressPush(true, true, true, true, true));

        // Horde <-> Non-horde (player/passive/normal mob): preserved when hordeToHordeOnly=true
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(true, false, true, true, true));
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(false, true, true, true, true));

        // Non-horde <-> Non-horde: never suppressed
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(false, false, true, true, true));
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(false, false, true, true, false));

        // When hordeToHordeOnly=false, any push involving a horde mob is suppressed
        assertTrue(HordeCollisionPolicy.shouldSuppressPush(true, false, true, true, false));
        assertTrue(HordeCollisionPolicy.shouldSuppressPush(false, true, true, true, false));

        // Disabled config disables suppression completely
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(true, true, false, true, true));
        assertFalse(HordeCollisionPolicy.shouldSuppressPush(true, true, true, false, true));
    }

    @Test
    void excludesHordeMobsFromPushScanToPreventCrammingAndQuadraticLoops() {
        // Horde mob scanning nearby entities excludes other horde mobs (prevents cramming + O(N^2) push)
        assertTrue(HordeCollisionPolicy.shouldExcludeFromPushScan(true, true, true, true, true, true));

        // Horde mob still detects non-horde entities (e.g. players) when hordeToHordeOnly=true
        assertFalse(HordeCollisionPolicy.shouldExcludeFromPushScan(true, false, true, true, true, true));

        // Non-horde mob's push scan is completely unaffected
        assertFalse(HordeCollisionPolicy.shouldExcludeFromPushScan(false, true, true, true, true, true));
        assertFalse(HordeCollisionPolicy.shouldExcludeFromPushScan(false, false, true, true, true, true));

        // Even if collision is toggled off, preventCramming alone still filters horde mobs from cramming scan
        assertTrue(HordeCollisionPolicy.shouldExcludeFromPushScan(true, true, true, false, true, true));

        // Fast pushEntities HEAD skip: skips AABB scan when hordeToHordeOnly=true and no player is within radius
        assertTrue(HordeCollisionPolicy.shouldSkipPushEntitiesForSelf(true, true, true, true, true, false));
        assertFalse(HordeCollisionPolicy.shouldSkipPushEntitiesForSelf(true, true, true, true, true, true));
        assertFalse(HordeCollisionPolicy.shouldSkipPushEntitiesForSelf(false, true, true, true, true, false));
        assertTrue(HordeCollisionPolicy.shouldSkipPushEntitiesForSelf(true, true, true, false, false, true));
    }

    @Test
    void configSaveAndLoadRoundtrip(@TempDir Path tempDir) {
        File cfgFile = tempDir.resolve("HordeOptimizer.properties").toFile();
        HordeOptimizerConfig.clusterCellSizeXZ = 8;
        HordeOptimizerConfig.maxThrottleInterval = 10;
        HordeOptimizerConfig.hordeToHordeCollisionOnly = false;
        HordeOptimizerConfig.save(cfgFile);

        HordeOptimizerConfig.resetDefaults();
        assertEquals(6, HordeOptimizerConfig.clusterCellSizeXZ);
        assertEquals(8, HordeOptimizerConfig.maxThrottleInterval);
        assertTrue(HordeOptimizerConfig.hordeToHordeCollisionOnly);

        HordeOptimizerConfig.loadOrCreate(cfgFile);
        assertEquals(8, HordeOptimizerConfig.clusterCellSizeXZ);
        assertEquals(10, HordeOptimizerConfig.maxThrottleInterval);
        assertFalse(HordeOptimizerConfig.hordeToHordeCollisionOnly);
    }
}
