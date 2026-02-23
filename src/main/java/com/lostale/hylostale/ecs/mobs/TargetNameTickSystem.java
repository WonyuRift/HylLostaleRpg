package com.lostale.hylostale.ecs.mobs;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.lostale.hylostale.ecs.health.HealthRegenManager;
import com.lostale.hylostale.services.mobs.MobInfoService;
import com.lostale.hylostale.services.mobs.MobLevelService;
import com.lostale.hylostale.ui.HylHud;
import com.lostale.hylostale.ui.HylHudManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Affiche un label HUD (#HudJoueur #TargetName) quand le joueur regarde une entité vivante (non joueur).
 * IMPORTANT:
 * - Ne crée pas de HUD dans le tick.
 * - Le HUD doit être créé/monté dans PlayerReadyEvent via HudManager.
 */
public final class TargetNameTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final MobInfoService mobInfo;
    private final MobLevelService mobLevels;
    private final HealthRegenManager combat;
    private static final long HOLD_MS = 2000L;

    private final Map<UUID, Long> holdUntil = new HashMap<>();
    private final Map<UUID, String> lastLine = new HashMap<>();

    private static final float TARGET_DISTANCE_BLOCKS = 6.0F;
    private static final int PERIOD_TICKS = 3; // ~150ms @20TPS

    private final Query<EntityStore> query =
            Query.and(new Query[]{Player.getComponentType(), PlayerRef.getComponentType()});

    private final HylHudManager huds;

    // throttle + anti-spam
    private final Map<UUID, Integer> tickCounterByPlayer = new HashMap<>();
    private final Map<UUID, Ref<EntityStore>> lastTargetByPlayer = new HashMap<>();

    public TargetNameTickSystem(HylHudManager huds, MobInfoService mobInfo, MobLevelService mobLevels, HealthRegenManager combat) {
        this.huds = huds;
        this.mobInfo = mobInfo;
        this.mobLevels = mobLevels;
        this.combat = combat;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetype, int archetypeChunkIndex) {
        return false;
    }

    @Override
    public void tick(float delta,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {

        try {
            Holder<EntityStore> holder = EntityUtils.toHolder(index, chunk);
            if (holder == null) return;

            ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
            PlayerRef playerRef = holder.getComponent(playerRefType);
            if (playerRef == null || !playerRef.isValid()) return;

            UUID playerId = playerRef.getUuid();

            // HUD déjà monté par PlayerReadyEvent
            HylHud hud = huds.getHud(playerId);
            if (hud == null) return;

            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                clear(playerId, hud);
                return;
            }

            // throttle par joueur
            int c = tickCounterByPlayer.getOrDefault(playerId, 0) + 1;
            if (c < PERIOD_TICKS) {
                tickCounterByPlayer.put(playerId, c);
                return;
            }
            tickCounterByPlayer.put(playerId, 0);

            Ref<EntityStore> targetRef;
            try {
                targetRef = TargetUtil.getTargetEntity(playerEntityRef, TARGET_DISTANCE_BLOCKS, store);
            } catch (Exception ex) {
                ((Api) LOGGER.atFine()).log("TargetUtil error for %s: %s", playerId, ex.getMessage());
                clear(playerId, hud);
                return;
            }

            // pas de cible
            if (targetRef == null || !targetRef.isValid() || targetRef.equals(playerEntityRef)) {
                clear(playerId, hud);
                return;
            }

            if (isDeadOrGone(targetRef, store)) {
                clearImmediate(playerId, hud);
                return;
            }

            // ignore joueurs
            Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
            if (targetPlayer != null) {
                clear(playerId, hud);
                return;
            }

            // filtre "vivant": doit avoir stat Health
            EntityStatMap stats = store.getComponent(targetRef, EntityStatMap.getComponentType());
            if (stats == null) {
                clear(playerId, hud);
                return;
            }

            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health == null) {
                clear(playerId, hud);
                return;
            }

            String name = mobInfo.getName(targetRef);
            if (name == null) name = "Cible";

            MobLevelService.MobState st = mobLevels.get(targetRef);

            String line;
            if (st != null) {
                line = name + "  Nv " + st.level() + "  " + st.hp() + "/" + st.maxHp();
            } else {
                // fallback sur HP vanilla si pas de state custom
                EntityStatValue hv = stats.get(DefaultEntityStatTypes.getHealth());
                if (hv != null) line = name + "  " + Math.round(hv.get()) + "/" + Math.round(hv.getMax());
                else line = name;
            }
            hud.setTargetName(line);

            holdUntil.put(playerId, System.currentTimeMillis() + HOLD_MS);
            lastLine.put(playerId, line);

            lastTargetByPlayer.put(playerId, targetRef);

        } catch (Throwable t) {
            ((Api) LOGGER.atSevere()).log("Critical error in TargetNameTickSystem: %s", t.getMessage());
            t.printStackTrace();
        }
    }

    private void clear(UUID playerId, HylHud hud) {
        long until = holdUntil.getOrDefault(playerId, 0L);

        // pendant le hold, on réaffiche la dernière ligne complète
        if (System.currentTimeMillis() < until) {
            String line = lastLine.get(playerId);
            if (line != null) hud.setTargetName(line);
            return;
        }

        // fin du hold : purge
        lastTargetByPlayer.remove(playerId);
        lastLine.remove(playerId);
        holdUntil.remove(playerId);
        hud.clearTargetName();
    }

    private boolean isDeadOrGone(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) return true;

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return true;

        EntityStatValue hv = stats.get(DefaultEntityStatTypes.getHealth());
        if (hv == null) return true;

        return hv.get() <= 0.0f;
    }

    private void clearImmediate(UUID playerId, HylHud hud) {
        lastTargetByPlayer.remove(playerId);
        lastLine.remove(playerId);
        holdUntil.remove(playerId);
        hud.clearTargetName();
    }
}