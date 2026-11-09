package com.corosus.zombieawareness;

import com.corosus.zombieawareness.client.SoundProfileEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ZAUtilUnitTest {
    @Test
    void soundProfileMatchingAndCachingWorks() {
        ZAUtil.listSoundProfiles.clear();
        ZAUtil.lookupSoundNameToProfileCache.clear();
        ZAUtil.lookupSoundIntToProfile.clear();

        SoundProfileEntry exact = new SoundProfileEntry("minecraft:entity.arrow.hit", 1.1D, false, 0);
        SoundProfileEntry partial = new SoundProfileEntry("block.note", 1.3D, true, 0).setMaxDistToSpawnFromPlayer(64);
        SoundProfileEntry intEntry = new SoundProfileEntry("debug-doors", 1.3D).setListSoundEventTypes(List.of(1005, 1006));
        ZAUtil.listSoundProfiles.add(exact);
        ZAUtil.listSoundProfiles.add(partial);
        ZAUtil.addSoundIntegerEntry(intEntry);

        assertSame(exact, ZAUtil.getSoundIDEntry("minecraft:entity.arrow.hit"));
        assertSame(partial, ZAUtil.getSoundIDEntry("minecraft:block.note_block.harp"));
        assertSame(intEntry, ZAUtil.getSoundIDEntry(1005));
        assertNull(ZAUtil.getSoundIDEntry("minecraft:ambient.cave"));
    }
}
