package com.lostale.hylostale.services.mobs;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MobLevelService {

    public record MobState(int level, int maxHp, int hp) {}

    private final Map<Ref<EntityStore>, MobState> data = new ConcurrentHashMap<>();

    public boolean has(Ref<EntityStore> ref) { return data.containsKey(ref); }

    public MobState get(Ref<EntityStore> ref) { return data.get(ref); }

    public void set(Ref<EntityStore> ref, MobState st) { data.put(ref, st); }

    public void remove(Ref<EntityStore> ref) { data.remove(ref); }

    public MobState damage(Ref<EntityStore> ref, int amount) {
        MobState st = data.get(ref);
        if (st == null) return null;
        int hp = Math.max(0, st.hp() - Math.max(0, amount));
        MobState next = new MobState(st.level(), st.maxHp(), hp);
        data.put(ref, next);
        return next;
    }

    public boolean isDead(MobState st) { return st != null && st.hp() <= 0; }
}
