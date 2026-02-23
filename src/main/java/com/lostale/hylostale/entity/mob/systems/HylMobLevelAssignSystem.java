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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

public class HylMobLevelAssignSystem extends EntityTickingSystem<EntityStore> {

    private static final int MIN_LVL = 1;
    private static final int MAX_LVL = 50;

    private final Query<EntityStore> query = Query.any();

    private final HylMobManager mobs;
    private final HylMobStatsService mobStats;

    public HylMobLevelAssignSystem(@Nonnull HylMobManager mobs,
                                   @Nonnull HylMobStatsService mobStats) {
        this.mobs = mobs;
        this.mobStats = mobStats;
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

        boolean hostile = isHostile(role);
        String name = extractMobName(role, npc);

        // Si tu veux limiter aux hostiles uniquement, décommente:
        // if (!hostile) return;

        int lvl = ThreadLocalRandom.current().nextInt(MIN_LVL, MAX_LVL + 1);

        mobStats.initMob(d, lvl, hostile, name);
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
}
