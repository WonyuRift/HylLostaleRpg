package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.service.ui.HylHudService;
import com.lostale.hylostale.ui.HylHud;

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
    private final Map<UUID, String> lastLine = new ConcurrentHashMap<>();
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

        Player player = holder.getComponent(Player.getComponentType());
        if (player == null) return;

        UUID pid = pref.getUuid();
        Ref<EntityStore> selfRef = pref.getReference();
        if (selfRef == null || !selfRef.isValid()) return;

        long now = System.currentTimeMillis();

        Ref<EntityStore> target;
        try {
            target = TargetUtil.getTargetEntity(selfRef, MAX_DISTANCE, store);
        } catch (Throwable ignored) {
            target = null;
        }

        // ignore self / invalid
        if (target != null && (!target.isValid() || target.equals(selfRef))) target = null;

        // ignore players as targets (optionnel)
        if (target != null) {
            Player tp = store.getComponent(target, Player.getComponentType());
            if (tp != null) target = null;
        }

        // cible actuelle -> construire ligne
        if (target != null) {

            // clear immédiat si la cible est "morte" (HP vanilla ou HP RPG)
            if (isTargetDead(target, store)) {
                clear(pid);
                return;
            }

            String line = buildLine(target, store);

            lastTarget.put(pid, target);
            lastLine.put(pid, line);
            keepUntilMs.put(pid, now + LINGER_MS);

            applyLine(pid, line);
            return;
        }

        // pas de cible -> linger
        Long until = keepUntilMs.get(pid);
        if (until != null && now < until) {
            String line = lastLine.get(pid);
            if (line != null && !line.isEmpty()) {
                applyLine(pid, line);
                return;
            }
        }

        clear(pid);
    }

    private void applyLine(UUID pid, String line) {
        HylHud hud = huds.getHud(pid);
        if (hud == null) return;
        hud.setTargetName(line);
    }

    private void clear(UUID pid) {
        lastTarget.remove(pid);
        lastLine.remove(pid);
        keepUntilMs.remove(pid);

        HylHud hud = huds.getHud(pid);
        if (hud == null) return;
        hud.clearTargetName();
    }

    private boolean isTargetDead(Ref<EntityStore> target, Store<EntityStore> store) {

        // si RPG mob data existe
        HylMobData md = mobs.peek(target);
        if (md != null && md.initialized) {
            return md.hp <= 0;
        }

        // fallback: health stat vanilla
        EntityStatMap stats = store.getComponent(target, EntityStatMap.getComponentType());
        if (stats == null) return false;

        EntityStatValue hp = stats.get(DefaultEntityStatTypes.getHealth());
        if (hp == null) return false;

        return hp.get() <= 0.0f;
    }

    private String buildLine(Ref<EntityStore> target, Store<EntityStore> store) {

        HylMobData md = mobs.peek(target);
        if (md != null && md.initialized) {
            // format: "Nom  Lv.X  HP a/b"
            return md.name + "  Lv." + md.level + "  HP " + md.hp + "/" + md.maxHp;
        }

        // fallback: au moins un HP vanilla si dispo
        EntityStatMap stats = store.getComponent(target, EntityStatMap.getComponentType());
        if (stats != null) {
            EntityStatValue hp = stats.get(DefaultEntityStatTypes.getHealth());
            if (hp != null) {
                int cur = (int) Math.floor(hp.get());
                int max = (int) Math.floor(hp.getMax());
                return "Cible  HP " + cur + "/" + max;
            }
        }

        return "Cible";
    }
}