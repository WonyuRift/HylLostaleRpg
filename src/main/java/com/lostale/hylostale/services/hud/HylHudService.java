package com.lostale.hylostale.services.hud;

import com.lostale.hylostale.store.HylData;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HylHudService {

    public record PlayerData(int level, int xp, int maxHp, int hp) {}

    private final HylData store;

    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();


    public HylHudService(HylData store) {
        this.store = store;
    }


        /* -----------------------------
       Loading / caching
       ----------------------------- */

    public PlayerData ensure(UUID uuid) {
        PlayerData st = cache.get(uuid);
        if (st != null) return st;

        // Load once from DB on cache miss
        PlayerData loaded = store.load(uuid);
        if (loaded == null) {
            loaded = defaultState();
        }

        PlayerData normalized = normalize(loaded);

        // Put normalized into cache
        PlayerData existing = cache.putIfAbsent(uuid, normalized);
        if (existing != null) return existing;

        // If we had to create/normalize, persist later
        if (!normalized.equals(loaded)) markDirty(uuid);

        return normalized;
    }

    private PlayerData defaultState() {
        int lvl = 1;
        int maxHp = computeMaxHp(lvl);
        return new PlayerData(lvl, 0, maxHp, maxHp);
    }

    private PlayerData normalize(PlayerData in) {
        int lvl = Math.max(1, in.level());
        int xp = Math.max(0, in.xp());

        int expectedMaxHp = computeMaxHp(lvl);

        int hp = clamp(in.hp(), 0, expectedMaxHp);

        // keep xp in [0, threshold) by rolling up levels (optional but consistent)
        while (xp >= nextThreshold(lvl)) {
            xp -= nextThreshold(lvl);
            lvl++;
            expectedMaxHp = computeMaxHp(lvl);
            hp = Math.min(hp, expectedMaxHp);
        }

        return new PlayerData(lvl, xp, expectedMaxHp, hp);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

        /* -----------------------------
       Derived stat formulas
       ----------------------------- */

    public int nextThreshold(int level) {
        int base = 100;
        int growth = level - 1;
        return base + 25 * growth * growth;
    }

    public int computeMaxHp(int level) {
        int base = 100;
        int linear = 2 * (level - 1);
        int soft = (int) Math.round(12.0 * Math.sqrt(level - 1));
        return base + linear + soft;
    }

        /* -----------------------------
       Mutations (cache only)
       ----------------------------- */

    public PlayerData addXp(UUID uuid, int delta) {
        PlayerData cur = ensure(uuid);

        int oldLvl = cur.level();
        int lvl = oldLvl;
        int xp = Math.max(0, cur.xp() + delta);

        while (xp >= nextThreshold(lvl)) {
            xp -= nextThreshold(lvl);
            lvl++;
        }

        int newMaxHp = computeMaxHp(lvl);

        int newHp;
        if (lvl > oldLvl) newHp = newMaxHp;
        else newHp = Math.min(cur.hp(), newMaxHp);

        PlayerData next = new PlayerData(lvl, xp, newMaxHp, newHp);
        cache.put(uuid, next);
        markDirty(uuid);
        return next;
    }

    public PlayerData damage(UUID uuid, int amount) {
        PlayerData st = ensure(uuid);
        int hp = Math.max(0, st.hp() - Math.max(0, amount));
        PlayerData next = new PlayerData(st.level(), st.xp(), st.maxHp(), hp);
        cache.put(uuid, next);
        markDirty(uuid);
        return next;
    }

    public PlayerData heal(UUID uuid, int amount) {
        PlayerData st = ensure(uuid);
        int hp = Math.min(st.maxHp(), st.hp() + Math.max(0, amount));
        PlayerData next = new PlayerData(st.level(), st.xp(), st.maxHp(), hp);
        cache.put(uuid, next);
        markDirty(uuid);
        return next;
    }

    public PlayerData setHp(UUID uuid, int hp) {
        PlayerData st = ensure(uuid);
        int v = clamp(hp, 0, st.maxHp());
        PlayerData next = new PlayerData(st.level(), st.xp(), st.maxHp(), v);
        cache.put(uuid, next);
        markDirty(uuid);
        return next;
    }

    public PlayerData setLevel(UUID uuid, int level) {
        PlayerData st = ensure(uuid);
        int lvl = Math.max(1, level);

        int maxHp = computeMaxHp(lvl);

        int hp = Math.min(st.hp(), maxHp);

        // keep xp within new threshold (optional)
        int xp = Math.min(st.xp(), nextThreshold(lvl) - 1);

        PlayerData next = new PlayerData(lvl, xp, maxHp, hp);
        cache.put(uuid, next);
        markDirty(uuid);
        return next;
    }

    /* -----------------------------
       Queries / formatting
       ----------------------------- */

    public boolean isDead(PlayerData st) {
        return st.hp() <= 0;
    }

    public String formatXp(PlayerData st) {
        return "Niveau: " + st.level() + " | XP: " + st.xp() + "/" + nextThreshold(st.level());
    }

        /* -----------------------------
       Persistence flushing
       ----------------------------- */

    /**
     * Flushes dirty players to DB.
     * Call this periodically (e.g., every 2–5 seconds) from your plugin timer.
     */
    public void flushDirty() {
        if (dirty.isEmpty()) return;

        // Iterate a snapshot to avoid holding up gameplay threads
        UUID[] ids = dirty.toArray(new UUID[0]);
        for (UUID id : ids) {
            PlayerData st = cache.get(id);
            if (st == null) {
                dirty.remove(id);
                continue;
            }
            store.upsert(id, st);
            dirty.remove(id);
        }
    }

    /**
     * Flushes everything currently in cache (useful on shutdown).
     */
    public void flushAll() {
        for (var e : cache.entrySet()) {
            store.upsert(e.getKey(), e.getValue());
        }
        dirty.clear();
    }

    /**
     * Optional: clear session cache for a player on disconnect.
     * If you call this, do it after a flush for that player or keep dirty batching.
     */
    public void evict(UUID uuid) {
        cache.remove(uuid);
        dirty.remove(uuid);
    }
}
