package com.lostale.hylostale.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class HylRegionLevelConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Range {
        public int min = 1;
        public int max = 5;

        public Range() {}
        public Range(int min, int max) { this.min = min; this.max = max; }
    }

    public static final class WorldCfg {
        public Range defaultRange = new Range(1, 5);
        public Map<String, Range> regions = new HashMap<>();
    }

    public Map<String, WorldCfg> worlds = new HashMap<>();

    public WorldCfg world(@Nonnull String worldKey) {
        return worlds.computeIfAbsent(worldKey.toLowerCase(Locale.ROOT), k -> new WorldCfg());
    }

    public Range getOrDefault(@Nonnull String worldKey, @Nonnull String regionId) {
        WorldCfg wc = worlds.get(worldKey.toLowerCase(Locale.ROOT));
        if (wc == null) return new Range(1, 5);
        Range r = wc.regions.get(regionId.toLowerCase(Locale.ROOT));
        if (r != null) return r;
        return (wc.defaultRange != null) ? wc.defaultRange : new Range(1, 5);
    }

    public void put(@Nonnull String worldKey, @Nonnull String regionId, int min, int max) {
        WorldCfg wc = world(worldKey);
        wc.regions.put(regionId.toLowerCase(Locale.ROOT), new Range(min, max));
    }

    public static @Nonnull HylRegionLevelConfig loadOrCreate(@Nonnull Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                HylRegionLevelConfig cfg = new HylRegionLevelConfig();
                save(file, cfg);
                return cfg;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                HylRegionLevelConfig cfg = GSON.fromJson(r, HylRegionLevelConfig.class);
                return cfg == null ? new HylRegionLevelConfig() : cfg;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load region config: " + file, e);
        }
    }

    public static void save(@Nonnull Path file, @Nonnull HylRegionLevelConfig cfg) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(cfg, w);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save region config: " + file, e);
        }
    }
}