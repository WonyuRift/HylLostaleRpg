package com.lostale.hylostale.service.region;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.lostale.hylostale.config.HylRegionLevelConfig;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HylRegionEditorService {

    public record View(String worldKey, String regionId, int min, int max, String status) {}

    private static final class Draft {
        String worldKey;
        String regionId;
        int min;
        int max;
        String status = "";
        int page = 0;
    }

    private final Path cfgFile;
    private final HylOrbisGuardRegionService resolver;

    private volatile HylRegionLevelConfig cfg;

    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();

    public HylRegionEditorService(@Nonnull Path cfgFile,
                                  @Nonnull HylRegionLevelConfig cfg,
                                  @Nonnull HylOrbisGuardRegionService resolver) {
        this.cfgFile = cfgFile;
        this.cfg = cfg;
        this.resolver = resolver;
    }

    public void openFor(@Nonnull Player p) {
        UUID id = p.getUuid();

        World w = Universe.get().getWorld(id); // conforme à tes commandes
        String worldKey = safeWorldKey(w);

        double x = p.getTransformComponent().getPosition().getX();
        double y = p.getTransformComponent().getPosition().getY();
        double z = p.getTransformComponent().getPosition().getZ();

        String regionId = String.valueOf(resolver.getRegionIdsAt(w, x, y, z));
        if (regionId == null || regionId.isBlank()) regionId = "default";

        HylRegionLevelConfig.Range r = cfg.getOrDefault(worldKey, regionId);

        Draft d = new Draft();
        d.worldKey = worldKey;
        d.regionId = regionId;
        d.min = clampMin(r.min);
        d.max = clampMax(r.max, d.min);
        d.status = "";

        drafts.put(id, d);
    }

    public void close(@Nonnull UUID playerUuid) {
        drafts.remove(playerUuid);
    }

    public @Nonnull View view(@Nonnull UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return new View("world", "default", 1, 5, "");
        return new View(d.worldKey, d.regionId, d.min, d.max, d.status);
    }

    public void bumpMin(@Nonnull UUID playerUuid, int delta) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        d.min = clampMin(d.min + delta);
        if (d.max < d.min) d.max = d.min;
        d.status = "";
    }

    public void bumpMax(@Nonnull UUID playerUuid, int delta) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        d.max = clampMax(d.max + delta, d.min);
        d.status = "";
    }

    public void reload(@Nonnull UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        this.cfg = HylRegionLevelConfig.loadOrCreate(cfgFile);
        HylRegionLevelConfig.Range r = cfg.getOrDefault(d.worldKey, d.regionId);
        d.min = clampMin(r.min);
        d.max = clampMax(r.max, d.min);
        d.status = "Reloaded";
    }

    public void save(@Nonnull UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;

        int min = clampMin(d.min);
        int max = clampMax(d.max, min);

        cfg.put(d.worldKey, d.regionId, min, max);
        HylRegionLevelConfig.save(cfgFile, cfg);

        d.min = min;
        d.max = max;
        d.status = "Saved";
    }

    private static int clampMin(int v) { return Math.max(1, v); }
    private static int clampMax(int v, int min) { return Math.max(min, v); }

    private static String safeWorldKey(World w) {
        try { return String.valueOf(w.getName()).toLowerCase(Locale.ROOT); }
        catch (Throwable t) { return "default"; }
    }

    public int getPage(UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        return d == null ? 0 : d.page;
    }

    public void pagePrev(UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        d.page = Math.max(0, d.page - 1);
    }

    public void pageNext(UUID playerUuid, int totalPages) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        d.page = Math.min(Math.max(0, totalPages - 1), d.page + 1);
    }

    public java.util.List<String> listRegions(UUID playerUuid) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return java.util.List.of();
        HylRegionLevelConfig.WorldCfg wc = cfg.world(d.worldKey);
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(wc.regions.keySet());
        ids.sort(String::compareToIgnoreCase);
        return ids;
    }

    public void selectRegion(UUID playerUuid, String regionId) {
        Draft d = drafts.get(playerUuid);
        if (d == null) return;
        if (regionId == null || regionId.isBlank()) return;

        d.regionId = regionId;

        HylRegionLevelConfig.Range r = cfg.getOrDefault(d.worldKey, d.regionId);
        d.min = Math.max(1, r.min);
        d.max = Math.max(d.min, r.max);
        d.status = "Selected: " + d.regionId;
    }

    public void syncOrbisRegionsIntoConfig(String worldKey) {
        HylRegionLevelConfig.WorldCfg wc = cfg.world(worldKey);

        if (wc.defaultRange == null) wc.defaultRange = new HylRegionLevelConfig.Range(1, 5);
        if (wc.regions == null) wc.regions = new HashMap<>();

        for (String id : resolver.getAllRegionIds(worldKey)) {
            if (id == null || id.isBlank()) continue;

            wc.regions.computeIfAbsent(id.toLowerCase(Locale.ROOT),
                    k -> new HylRegionLevelConfig.Range(wc.defaultRange.min, wc.defaultRange.max));
        }
    }
}