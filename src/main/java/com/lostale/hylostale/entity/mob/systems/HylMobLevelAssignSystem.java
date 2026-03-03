package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.lostale.hylostale.config.HylRegionLevelConfig;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;
import com.lostale.hylostale.service.region.HylOrbisGuardRegionService;

import javax.annotation.Nonnull;
import java.util.*;

public class HylMobLevelAssignSystem extends EntityTickingSystem<EntityStore> {

    private static final int PERIOD_TICKS = 10; // throttle CPU
    private int counter = 0;

    private final Query<EntityStore> query = Query.any();

    private final HylMobManager mobs;
    private final HylMobStatsService mobStats;

    private final HylRegionLevelConfig regionCfg;
    private final HylOrbisGuardRegionService regionResolver;

    // cache région par chunk
    private static final long REGION_CACHE_TTL_MS = 2500L;

    private record ChunkKey(String worldId, int cx, int cz) {}
    private static final class CachedRule {
        final HylRegionLevelConfig.Range range;
        final long expireAt;
        CachedRule(HylRegionLevelConfig.Range range, long expireAt) { this.range = range; this.expireAt = expireAt; }
    }
    private final Map<ChunkKey, CachedRule> cache = new HashMap<>();

    public HylMobLevelAssignSystem(@Nonnull HylMobManager mobs,
                                   @Nonnull HylMobStatsService mobStats,
                                   @Nonnull HylRegionLevelConfig regionCfg,
                                   @Nonnull HylOrbisGuardRegionService regionResolver) {
        this.mobs = mobs;
        this.mobStats = mobStats;
        this.regionCfg = regionCfg;
        this.regionResolver = regionResolver;
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetype, int archetypeChunkIndex) {
        return false;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> cb) {

        // throttling global
        counter++;
        if (counter < PERIOD_TICKS) return;
        counter = 0;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        // Ignore joueurs
        Player pl = store.getComponent(ref, Player.getComponentType());
        if (pl != null) return;

        // Uniquement NPC
        Object ent = EntityUtils.getEntity(index, chunk);
        if (!(ent instanceof NPCEntity npc)) return;

        HylMobData d = mobs.getOrCreate(ref);
        if (d.initialized) return;

        // Détermine hostile + nom
        Role role = null;
        try { role = npc.getRole(); } catch (Throwable ignored) {}

        WorldSupport ws = role.getWorldSupport();
        if (ws == null) return;

        boolean hostile = isHostile(role);
        String name = extractMobName(role, npc);

        // Si tu veux limiter aux hostiles uniquement, décommente:
        // if (!hostile) return;

        double x = 0, y = 0, z = 0;
        try {
            // selon API : transform component / position
            var t = npc.getTransformComponent();   // adapte à ta build
            x = t.getPosition().getX();
            y = t.getPosition().getY();
            z = t.getPosition().getZ();
        } catch (Throwable ignored) {}

        EntityStore ext = store.getExternalData();
        World world = ext.getWorld();
        if (world == null) return;

        String worldId;
        try { worldId = world.getName(); }
        catch (Throwable t) { worldId = "world"; }

        // resolve range via region (cached)
        HylRegionLevelConfig.Range range = resolveRange(world, worldId, x, y, z);

        long entityKey = computeEntityKey(npc, ref);;
        int lvl = pickLevelStable(range.min, range.max, 0L, entityKey);
        List<String> ids = regionResolver.getRegionIdsAt(world, x, y, z);

        mobStats.initMob(d, lvl, hostile, name);
        d.spawnWorld = worldId;
        d.spawnRegion = pickBestRegionId(worldId, ids); // ou best id

        HylMobHpScalerSystem.apply(store, cb, ref, d);
    }

    private static boolean isHostile(Role role) {
        if (role == null) return false;
        try {
            WorldSupport ws = role.getWorldSupport();
            if (ws == null) return false;
            return ws.getDefaultPlayerAttitude() == Attitude.HOSTILE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String extractMobName(Role role, NPCEntity npc) {
        String s = null;

        try {
            if (role != null) s = String.valueOf(role.getRoleName());
        } catch (Throwable ignored) {}

        if (s == null || s.isBlank()) {
            try {
                if (role != null) s = String.valueOf(role);
            } catch (Throwable ignored) {}
        }

        if (s == null || s.isBlank()) {
            s = npc.getClass().getSimpleName();
        }

        return formatMobName(s);
    }

    private static String formatMobName(String raw) {
        if (raw == null) return "Mob";
        String s = raw.trim();
        if (s.isEmpty()) return "Mob";

        // retire chemins/ids éventuels
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < s.length()) s = s.substring(slash + 1);

        // Snake_case / kebab-case -> spaces
        s = s.replace('_', ' ').replace('-', ' ');

        // CamelCase -> spaces
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2");

        // normalise espaces
        s = s.replaceAll("\\s+", " ").trim();

        // Title case simple
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1).toLowerCase());
            out.append(' ');
        }
        s = out.toString().trim();

        return s.isEmpty() ? "Mob" : s;
    }

    private HylRegionLevelConfig.Range resolveRange(World world, String worldId, double x, double y, double z) {
        int cx = (int)Math.floor(x) >> 4;
        int cz = (int)Math.floor(z) >> 4;

        long now = System.currentTimeMillis();
        ChunkKey key = new ChunkKey(worldId, cx, cz);

        CachedRule cached = cache.get(key);
        if (cached != null && cached.expireAt > now) return cached.range;

        List<String> ids = regionResolver.getRegionIdsAt(world, x, y, z);

        String regionId = (ids == null || ids.isEmpty()) ? "default" : ids.get(0);
        String chosen = pickBestRegionId(worldId, ids);

        HylRegionLevelConfig.Range range = regionCfg.getOrDefault(worldId, chosen);

        cache.put(key, new CachedRule(range, now + REGION_CACHE_TTL_MS));
        return range;
    }

    private String pickBestRegionId(String worldId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return "default";

        HylRegionLevelConfig.WorldCfg wc = regionCfg.world(worldId); // garantit world cfg
        if (wc.regions == null || wc.regions.isEmpty()) return "default";

        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            if (wc.regions.containsKey(id.toLowerCase(Locale.ROOT))) {
                return id.toLowerCase(Locale.ROOT);
            }
        }
        return "default";
    }

    private static long computeEntityKey(NPCEntity npc, Ref<EntityStore> ref) {
        // 1) uuid si exposé
        try {
            UUID u = npc.getUuid();
            if (u != null) return u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        } catch (Throwable ignored) {}

        // 2) entityId si exposé
        try {
            long id = Long.parseLong(npc.getNPCTypeId());
            if (id != 0L) return id;
        } catch (Throwable ignored) {}

        // 3) fallback (stable session uniquement)
        return (long) ref.hashCode();
    }

    private static int pickLevelStable(int min, int max, long seed, long entityKey) {
        int lo = Math.max(1, Math.min(min, max));
        int hi = Math.max(lo, Math.max(min, max));

        long h = seed ^ (entityKey * 0x9E3779B97F4A7C15L);
        h = mix64(h);

        int span = (hi - lo) + 1;
        int off = (int) Math.floorMod(h, span);
        return lo + off;
    }

    private static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }
}
