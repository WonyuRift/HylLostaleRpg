package com.lostale.hylostale.service.region;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.orbisguard.api.OrbisGuardAPI;
import com.orbisguard.api.region.IRegion;
import com.orbisguard.api.region.IRegionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class HylOrbisGuardRegionService {

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    @Nonnull
    public List<String> getRegionIdsAt(@Nonnull World world, double x, double y, double z) {
        OrbisGuardAPI api;
        try {
            api = OrbisGuardAPI.getInstance();
        } catch (Throwable t) {
            LOG.atWarning().log("[OG] getInstance failed: %s", t.toString());
            return List.of();
        }

        if (api == null) {
            LOG.atInfo().log("[OG] api=null (OrbisGuard pas prêt / pas chargé)");
            return List.of();
        }

        String wName;
        try { wName = String.valueOf(world.getName()); }
        catch (Throwable t) { wName = "world"; }

        String key = wName.toLowerCase(Locale.ROOT);

        IRegionManager mgr = null;

        // Essais dans l’ordre (les serveurs OrbisGuard utilisent souvent "world")
        for (String k : List.of(key, "world", "default", "overworld")) {
            try {
                mgr = api.getRegionContainer().getRegionManager(k);
            } catch (Throwable ignored) {}
            if (mgr != null) {
                //LOG.atInfo().log("[OG] manager ok key=%s (worldName=%s)", k, wName);
                break;
            }
        }

        if (mgr == null) {
            LOG.atWarning().log("[OG] manager=null (worldName=%s key=%s). OrbisGuard n'a pas de RegionManager pour ce monde.", wName, key);
            return List.of();
        }

        Set<IRegion> regs;
        try {
            regs = mgr.getRegionsAt((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
        } catch (Throwable t) {
            LOG.atWarning().log("[OG] getRegionsAt failed: %s", t.toString());
            return List.of();
        }

        if (regs == null || regs.isEmpty()) return List.of();

        return regs.stream()
                .map(r -> safeId(r).toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private static String safeId(IRegion r) {
        try { return String.valueOf(r.getId()); }
        catch (Throwable t) { return "unknown"; }
    }

    @Nonnull
    public List<String> getAllRegionIds(@Nonnull String worldKey) {
        OrbisGuardAPI api;
        try { api = OrbisGuardAPI.getInstance(); } catch (Throwable t) { return List.of(); }
        if (api == null) return List.of();

        String key = worldKey.toLowerCase(Locale.ROOT);

        IRegionManager mgr = null;
        for (String k : List.of(key, "world", "default", "overworld")) {
            try { mgr = api.getRegionContainer().getRegionManager(k); } catch (Throwable ignored) {}
            if (mgr != null) break;
        }
        if (mgr == null) return List.of();

        Collection<IRegion> regs;
        try { regs = mgr.getRegions(); } catch (Throwable t) { return List.of(); }
        if (regs == null || regs.isEmpty()) return List.of();

        return regs.stream()
                .map(r -> safeId(r).toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }
}
