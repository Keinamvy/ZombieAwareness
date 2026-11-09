package com.corosus.modconfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CoroConfigRegistry {

    private static CoroConfigRegistry instance;

    private final File baseConfigDir;
    public final List<IConfigCategory> configs = new ArrayList<>();
    public final ConcurrentHashMap<String, IConfigCategory> lookupRegistryNameToConfig = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<String, File> lookupRegistryNameToFile = new ConcurrentHashMap<>();

    public CoroConfigRegistry() {
        this(new File("./config"));
    }

    public CoroConfigRegistry(File baseConfigDir) {
        this.baseConfigDir = baseConfigDir;
    }

    public static synchronized CoroConfigRegistry instance() {
        if (instance == null) {
            instance = new CoroConfigRegistry();
        }
        return instance;
    }

    public synchronized void addConfigFile(String modID, IConfigCategory configCat) {
        configs.add(configCat);
        lookupRegistryNameToConfig.put(configCat.getRegistryName(), configCat);

        File cfgFile = resolveConfigFile(configCat);
        lookupRegistryNameToFile.put(configCat.getRegistryName(), cfgFile);

        if (cfgFile.exists()) {
            loadFromFile(configCat, cfgFile);
        }
        saveToFile(configCat, cfgFile);
        configCat.hookUpdatedValues();
    }

    public synchronized void forceLoadRuntimeSettingsFromFile() {
        for (IConfigCategory cat : configs) {
            File cfgFile = lookupRegistryNameToFile.get(cat.getRegistryName());
            if (cfgFile != null && cfgFile.exists()) {
                loadFromFile(cat, cfgFile);
                cat.hookUpdatedValues();
            }
        }
    }

    public synchronized void forceSaveAllFilesFromRuntimeSettings() {
        for (IConfigCategory cat : configs) {
            File cfgFile = lookupRegistryNameToFile.get(cat.getRegistryName());
            if (cfgFile != null) {
                saveToFile(cat, cfgFile);
            }
        }
    }

    private File resolveConfigFile(IConfigCategory cat) {
        String fileName = cat.getConfigFileName();
        if (!fileName.endsWith(".cfg")) {
            fileName = fileName + ".cfg";
        }
        File file = new File(fileName);
        if (!file.isAbsolute()) {
            file = new File(baseConfigDir, fileName);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }

    private void loadFromFile(IConfigCategory cat, File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eqIdx).trim();
                String val = trimmed.substring(eqIdx + 1).trim();
                applyField(cat, key, val);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void applyField(IConfigCategory cat, String fieldName, String rawValue) {
        try {
            Field field = cat.getClass().getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                return;
            }
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == int.class || type == Integer.class) {
                field.set(null, Integer.parseInt(rawValue));
            } else if (type == double.class || type == Double.class) {
                field.set(null, Double.parseDouble(rawValue));
            } else if (type == float.class || type == Float.class) {
                field.set(null, Float.parseFloat(rawValue));
            } else if (type == long.class || type == Long.class) {
                field.set(null, Long.parseLong(rawValue));
            } else if (type == boolean.class || type == Boolean.class) {
                field.set(null, Boolean.parseBoolean(rawValue));
            } else if (type == String.class) {
                field.set(null, rawValue);
            }
        } catch (NoSuchFieldException ignored) {
            // Ignore unknown fields in config file
        } catch (Exception ignored) {
            // Keep default value on malformed value
        }
    }

    private void saveToFile(IConfigCategory cat, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("# Configuration file: " + cat.getCategory());
            writer.newLine();
            writer.newLine();
            for (Field field : cat.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                ConfigComment comment = field.getAnnotation(ConfigComment.class);
                if (comment != null) {
                    for (String c : comment.value()) {
                        writer.write("# " + c);
                        writer.newLine();
                    }
                }
                Object val = field.get(null);
                writer.write(field.getName() + "=" + (val != null ? val.toString() : ""));
                writer.newLine();
                writer.newLine();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
