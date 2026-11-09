package com.corosus.modconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class CoroConfigRegistryTest {
    public static class DummyConfig implements IConfigCategory {
        @ConfigComment("Max range")
        public static int maxRange = 64;
        public static boolean enabled = true;
        public static double multiplier = 1.5D;
        public static String whitelist = "PlayerA, PlayerB";
        public boolean hooked = false;
        private final String fileName;
        public DummyConfig(String fileName) { this.fileName = fileName; }
        @Override public String getName() { return "Dummy"; }
        @Override public String getRegistryName() { return "dummy"; }
        @Override public String getConfigFileName() { return fileName; }
        @Override public String getCategory() { return "Dummy"; }
        @Override public void hookUpdatedValues() { hooked = true; }
    }

    @Test
    void writesDefaultAndLoadsModifiedAndIgnoresMalformedLines(@TempDir Path tempDir) throws Exception {
        File cfgFile = tempDir.resolve("Dummy.cfg").toFile();
        DummyConfig cfg = new DummyConfig("Dummy");
        CoroConfigRegistry reg = new CoroConfigRegistry(tempDir.toFile());
        reg.addConfigFile("zombieawareness", cfg);
        assertTrue(cfgFile.exists());
        assertTrue(cfg.hooked);

        Files.writeString(cfgFile.toPath(), "# comment\n\nmalformed_line_without_equals\nunknownField=999\nmaxRange=128\nenabled=false\nmultiplier=2.25\nwhitelist=Bob\n");
        cfg.hooked = false;
        reg.forceLoadRuntimeSettingsFromFile();
        assertEquals(128, DummyConfig.maxRange);
        assertFalse(DummyConfig.enabled);
        assertEquals(2.25D, DummyConfig.multiplier, 0.001D);
        assertEquals("Bob", DummyConfig.whitelist);
        assertTrue(cfg.hooked);
    }
}
