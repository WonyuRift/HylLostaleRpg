package com.lostale.hylostale.entity.player;

import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.data.repo.player.HylPlayerRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HylPlayerManager {

    private final HylPlayerRepository repo;
    private final Map<UUID, HylPlayerData> cache = new ConcurrentHashMap<>();

    public HylPlayerManager(HylPlayerRepository repo) {
        this.repo = repo;
    }

    /** Charge (ou crée) et renvoie les données RPG du joueur. */
    public HylPlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, repo::loadOrCreate);
    }

    /** Renvoie null si pas en cache (utile si le joueur n'est pas encore prêt). */
    public HylPlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    /** Sauvegarde les données du joueur si présentes. */
    public void save(UUID uuid) {
        HylPlayerData d = cache.get(uuid);
        if (d != null) repo.save(d);
    }

    /** Sauvegarde toutes les données en cache. */
    public void saveAll() {
        for (HylPlayerData d : cache.values()) {
            repo.save(d);
        }
    }

    /** Décharge un joueur (save + remove). À appeler sur disconnect. */
    public void unload(UUID uuid) {
        HylPlayerData d = cache.remove(uuid);
        if (d != null) repo.save(d);
    }

    /** Purge le cache (saveAll avant en général). */
    public void clear() {
        cache.clear();
    }
}
