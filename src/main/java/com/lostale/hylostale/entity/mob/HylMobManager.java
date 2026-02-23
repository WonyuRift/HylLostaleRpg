package com.lostale.hylostale.entity.mob;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.data.mob.HylMobData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HylMobManager {
    private final Map<Ref<EntityStore>, HylMobData> cache = new ConcurrentHashMap<>();

    public HylMobData getOrCreate(Ref<EntityStore> ref) {
        return cache.computeIfAbsent(ref, HylMobData::new);
    }

    public HylMobData peek(Ref<EntityStore> ref) {
        return cache.get(ref);
    }

    public boolean has(Ref<EntityStore> ref) {
        return cache.containsKey(ref);
    }

    public void remove(Ref<EntityStore> ref) {
        cache.remove(ref);
    }
}
