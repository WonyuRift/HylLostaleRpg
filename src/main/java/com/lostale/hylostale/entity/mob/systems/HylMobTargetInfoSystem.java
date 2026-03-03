package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.service.ui.HylHudService;
import com.lostale.hylostale.ui.hud.HylHud;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HylMobTargetInfoSystem extends EntityTickingSystem<EntityStore> {

    private static final float MAX_DISTANCE = 6.0F;
    private static final long LINGER_MS = 2000L;

    private final Query<EntityStore> query = Query.and(new Query[]{
            Player.getComponentType(),
            PlayerRef.getComponentType()
    });

    private final HylHudService huds;
    private final HylMobManager mobs;

    // par joueur
    private final Map<UUID, Ref<EntityStore>> lastTarget = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastName = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastLevel = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastHp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastMaxHp = new ConcurrentHashMap<>();
    private final Map<UUID, Long> keepUntilMs = new ConcurrentHashMap<>();

    public HylMobTargetInfoSystem(@Nonnull HylHudService huds, @Nonnull HylMobManager mobs) {
        this.huds = huds;
        this.mobs = mobs;
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

        var holder = EntityUtils.toHolder(index, chunk);
        if (holder == null) return;

        PlayerRef pref = holder.getComponent(PlayerRef.getComponentType());
        if (pref == null || !pref.isValid()) return;

        UUID pid = pref.getUuid();
        Ref<EntityStore> selfRef = pref.getReference();
        if (selfRef == null || !selfRef.isValid()) return;

        long now = System.currentTimeMillis();

        Ref<EntityStore> target = null;
        try { target = TargetUtil.getTargetEntity(selfRef, MAX_DISTANCE, store); } catch (Throwable ignored) {}

        if (target != null && (!target.isValid() || target.equals(selfRef))) target = null;

        // --- cible valide ---
        if (target != null) {
            HylMobData md = mobs.peek(target);
            if (md != null && md.initialized && md.hp <= 0) {
                clear(pid);
                return;
            }

            // 2 champs séparés
            if (md != null) {
                String name = (md != null && md.initialized) ? md.name : "Cible";
                String lvl = (md != null && md.initialized) ? "" + md.level : "";
                int hp = md.hp;
                int maxHp = md.maxHp;


                    lastTarget.put(pid, target);
                    lastName.put(pid, name);
                    lastLevel.put(pid, lvl);
                    lastHp.put(pid, hp);
                    lastMaxHp.put(pid, maxHp);
                    keepUntilMs.put(pid, now + LINGER_MS);

                    apply(pid, name, lvl, hp, maxHp);
            }
            return;
        }

        // --- pas de cible -> linger ---
        Long until = keepUntilMs.get(pid);
        if (until != null && now < until) {
            String name = lastName.get(pid);
            String lvl = lastLevel.get(pid);
            int hp = lastHp.get(pid);
            int maxHp = lastMaxHp.get(pid);
            if (name != null) {
                apply(pid, name, (lvl == null ? "" : lvl), hp, maxHp);
                return;
            }
        }

        clear(pid);
    }

    private void apply(UUID pid, String name, String lvl, int hp, int maxHp) {
        HylHud hud = huds.getHud(pid);
        if (hud == null) return;
        hud.setTargetName(name);
        hud.setTargetLevel(lvl);
        hud.setTargetHp(hp, maxHp);
    }

    private void clear(UUID pid) {
        lastTarget.remove(pid);
        lastName.remove(pid);
        lastLevel.remove(pid);
        lastHp.remove(pid);
        lastMaxHp.remove(pid);
        keepUntilMs.remove(pid);

        HylHud hud = huds.getHud(pid);
        if (hud == null) return;
        hud.clearTarget();
    }
}