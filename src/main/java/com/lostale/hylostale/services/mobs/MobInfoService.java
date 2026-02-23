package com.lostale.hylostale.services.mobs;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MobInfoService {

    private final Map<Ref<EntityStore>, String> names = new ConcurrentHashMap<>();

    public void setName(Ref<EntityStore> ref, String name) {
        if (ref == null || name == null) return;
        String v = name.trim();
        if (v.isEmpty()) return;
        names.put(ref, v);
    }

    public String getName(Ref<EntityStore> ref) {
        String v = names.get(ref);
        return (v == null || v.isBlank()) ? null : v;
    }

    public void remove(Ref<EntityStore> ref) {
        names.remove(ref);
    }
}