package com.corosus.zombieawareness;

import com.corosus.coroutil.util.CoroUtilEntity;
import com.corosus.coroutil.util.CoroUtilPath;
import com.corosus.modconfig.CoroConfigRegistry;
import com.corosus.zombieawareness.client.SoundProfileEntry;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyAndChunkGuardTest {

    @BeforeEach
    void setUp() {
        ZombieAwareness.clearEntityData();
        ZombieAwareness.unitTest.clear();
    }

    @AfterEach
    void tearDown() {
        ZombieAwareness.clearEntityData();
        ZombieAwareness.unitTest.clear();
    }

    @Test
    void concurrentEntityDataAndUnitTestMapsHandleParallelAccessAndCleanup() throws Exception {
        assertInstanceOf(ConcurrentHashMap.class, ZombieAwareness.entityData);
        assertInstanceOf(ConcurrentHashMap.class, ZombieAwareness.unitTest);

        // Read-only lookups must never allocate or insert entries into entityData
        assertNull(ZombieAwareness.getPersistentDataOrNull(null));
        assertEquals(0L, ZombieAwareness.getLastActionTick(null));
        assertTrue(ZombieAwareness.entityData.isEmpty());
        assertNotNull(ZombieAwareness.getPersistentDataSafe(null));

        boolean prevUnitTestFlag = ZombieAwareness.debugUnitTestEnabled;
        ZombieAwareness.debugUnitTestEnabled = false;
        ZombieAwareness.unitTest("suppressed_when_disabled");
        assertFalse(ZombieAwareness.unitTest.containsKey("suppressed_when_disabled"));

        ZombieAwareness.debugUnitTestEnabled = true;
        int threads = 6;
        int entriesPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int workerId = t;
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    for (int i = 0; i < entriesPerThread; i++) {
                        UUID id = UUID.randomUUID();
                        CompoundTag tag = new CompoundTag();
                        tag.putLong(ZAUtil.ZA_LAST_ACTION, workerId * 1000L + i);
                        ZombieAwareness.entityData.put(id, tag);
                        ZombieAwareness.unitTest("step_" + workerId + "_" + i);
                        assertNotNull(ZombieAwareness.entityData.get(id));
                        if (i % 2 == 0) {
                            ZombieAwareness.removeEntityData(id);
                        }
                    }
                    return null;
                }));
            }

            startLatch.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            ZombieAwareness.debugUnitTestEnabled = prevUnitTestFlag;
        }

        assertEquals(threads * (entriesPerThread / 2), ZombieAwareness.entityData.size());
        assertEquals(threads * entriesPerThread, ZombieAwareness.unitTest.size());

        ZombieAwareness.clearEntityData();
        assertTrue(ZombieAwareness.entityData.isEmpty());
    }

    @Test
    void staticCollectionsInZAUtilAndCoroConfigRegistryAreThreadSafe() {
        assertInstanceOf(CopyOnWriteArrayList.class, ZAUtil.listSoundProfiles);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lookupSoundNameToProfileCache);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lookupSoundIntToProfile);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lastHealths);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lastBleedTimes);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lookupLastWaypointTime);
        assertInstanceOf(ConcurrentHashMap.class, ZAUtil.lookupTickableEntitiesCache);
        assertInstanceOf(CopyOnWriteArrayList.class, CoroConfigRegistry.instance().configs);

        ZAUtil.listSoundProfiles.clear();
        ZAUtil.lookupSoundNameToProfileCache.clear();
        ZAUtil.listSoundProfiles.add(new SoundProfileEntry("test.sound", 1.0D));
        assertNotNull(ZAUtil.getSoundIDEntry("test.sound"));

        // Misses are memoized via sentinel so repeated ambient sounds avoid O(N) list re-scans
        assertNull(ZAUtil.getSoundIDEntry("minecraft:ambient.cave"));
        assertTrue(ZAUtil.lookupSoundNameToProfileCache.containsKey("minecraft:ambient.cave"));
        assertNull(ZAUtil.getSoundIDEntry("minecraft:ambient.cave"));

        ZAUtil.listSoundProfiles.clear();
        ZAUtil.lookupSoundNameToProfileCache.clear();
    }

    @Test
    void offThreadAndNullLevelGuardsBlockUnsafeServerLevelDispatch() {
        assertTrue(ZAUtil.isSafeServerLevelDispatch(true, true, true));
        assertFalse(ZAUtil.isSafeServerLevelDispatch(false, true, true), "WorldGenRegion / ClientLevel must be blocked");
        assertFalse(ZAUtil.isSafeServerLevelDispatch(true, false, true), "Missing server must be blocked");
        assertFalse(ZAUtil.isSafeServerLevelDispatch(true, true, false), "Off-thread worker (e.g. c2me-worker) must be blocked");

        assertFalse(ZAUtil.isServerMainThread(null));
        assertFalse(CoroUtilPath.isBlockChunkLoaded(null, 0, 0));
        assertNull(ZAUtil.spawnOrBuffSenseAtPos(null, new Vector3d(0, 64, 0), EnumSenseType.SOUND, 60, true));
    }

    @Test
    void rayChunkSamplerDetectsUnloadedIntermediateChunksBeforeClip() {
        // Ray from (8, 8) [chunk 0,0] to (56, 8) [chunk 3,0] passes through chunks (0,0), (1,0), (2,0), (3,0)
        assertTrue(CoroUtilEntity.areAllSampledRayChunksLoaded(8.0, 8.0, 56.0, 8.0, (cx, cz) -> cz == 0 && cx >= 0 && cx <= 3));

        // If intermediate chunk (2, 0) is unloaded even though endpoints (0,0) and (3,0) are loaded -> must return false
        assertFalse(CoroUtilEntity.areAllSampledRayChunksLoaded(8.0, 8.0, 56.0, 8.0,
                (cx, cz) -> cz == 0 && (cx == 0 || cx == 1 || cx == 3)));

        assertFalse(CoroUtilEntity.areAllSampledRayChunksLoaded(0.0, 0.0, 16.0, 16.0, null));
    }
}
