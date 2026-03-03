package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;
import com.lostale.hylostale.service.mob.HylMobXpService;
import com.lostale.hylostale.service.player.HylPlayerExpService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;
import com.lostale.hylostale.utils.math.HylDamageScaling;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HylMobDamageFilterSystem extends DamageEventSystem {

    private static final float LETHAL_DAMAGE = 999999f;

    private final HylMobManager mobs;
    private final HylMobStatsService mobStats;
    private final HylMobXpService mobXp;

    private final HylPlayerManager players;
    private final HylPlayerExpService xp;
    private final HylPlayerStatsService playerStats;
    private final HylHudService huds;
    private final HylConfig config;

    private static volatile boolean DUMPED_DAMAGE = false;

    private final Map<Ref<EntityStore>, UUID> lastDamager = new ConcurrentHashMap<>();

    public HylMobDamageFilterSystem(@Nonnull HylMobManager mobs,
                                    @Nonnull HylMobStatsService mobStats,
                                    @Nonnull HylMobXpService mobXp,
                                    @Nonnull HylPlayerManager players,
                                    @Nonnull HylPlayerExpService xp,
                                    @Nonnull HylPlayerStatsService playerStats,
                                    @Nonnull HylHudService huds,
                                    @Nonnull HylConfig config) {
        this.mobs = mobs;
        this.mobStats = mobStats;
        this.mobXp = mobXp;
        this.players = players;
        this.xp = xp;
        this.playerStats = playerStats;
        this.huds = huds;
        this.config = config;
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public @Nonnull SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {


        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        if (!DUMPED_DAMAGE) {
            DUMPED_DAMAGE = true;
            dumpDamage(damage);
        }

        // Ignore joueurs (HP RPG joueur géré ailleurs)
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer != null) return;

        // Mob data requise
        HylMobData mob = mobs.peek(targetRef);
        if (mob == null || !mob.initialized) return;

        // Mémorise le dernier joueur ayant touché (si disponible)
        UUID attacker = tryGetAttackerPlayerUuid(damage, store);
        if (attacker != null) {
            lastDamager.put(targetRef, attacker);
        }

        int raw = (int) Math.ceil(Math.max(0f, damage.getAmount()));
        if (raw <= 0) return;

        // compute scaled
        int attackerLvl = (attacker != null) ? playerStats.level(attacker) : mob.level; // fallback neutre
        int attackerAtk = (attacker != null) ? playerStats.attack(attacker) : 0;

        int mobLvl = mob.level;
        int mobDef = mobStats.defense(mobLvl);

        int scaled = HylDamageScaling.scale(
                config, raw, attackerLvl, attackerAtk, mobLvl, mobDef
        );

        // Annule dégâts vanilla et applique dégâts RPG
        damage.setCancelled(true);
        mobStats.damage(mob, scaled);

        HylMobHpScalerSystem.apply(store, commandBuffer, targetRef, mob);
        // Mort RPG -> déclencher mort vanilla + XP
        if (mob.hp > 0) return;

        // XP reward 1 seule fois
        if (!mob.rewarded) {
            mob.rewarded = true;

            UUID killer = lastDamager.get(targetRef);
            if (killer != null) {
                HylPlayerData pd = players.get(killer);
                int gained = mobXp.computeKillXp(pd.level, mob.level);

                int before = pd.level;
                xp.addXp(killer, gained);
                int after = players.get(killer).level;

                if (after > before) {
                    playerStats.recompute(killer, true);
                    //playerStats.healToFull(killer);
                }

                huds.renderXp(killer);
                huds.renderHealth(killer);
            }
        }

        // Nettoyage local (pas obligatoire mais évite fuite mémoire)
        lastDamager.remove(targetRef);
        mobs.remove(targetRef);

        // Laisse le jeu tuer le mob (drops + anim)
        damage.setCancelled(false);
        damage.setAmount(LETHAL_DAMAGE);
    }

    @SuppressWarnings("unchecked")
    private UUID tryGetAttackerPlayerUuid(@Nonnull Damage damage, @Nonnull Store<EntityStore> store) {

        Object src;
        try {
            src = damage.getSource(); // Damage$Source
        } catch (Throwable ignored) {
            return null;
        }
        if (src == null) return null;

        // 1) Méthode la plus probable: source -> Ref<EntityStore> direct
        UUID u = scanNoArgRefGetters(src, store);
        if (u != null) return u;

        // 2) Parfois: source -> PlayerRef
        u = scanNoArgPlayerRefGetters(src);
        if (u != null) return u;

        // 3) Parfois: source -> UUID
        u = scanNoArgUuidGetters(src);
        if (u != null) return u;

        // 4) Certains sources encapsulent encore un nested (ex: getEntitySource())
        Object nested = scanNoArgNonJavaObject(src);
        if (nested != null) {
            u = scanNoArgRefGetters(nested, store);
            if (u != null) return u;

            u = scanNoArgPlayerRefGetters(nested);
            if (u != null) return u;

            u = scanNoArgUuidGetters(nested);
            if (u != null) return u;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private UUID scanNoArgRefGetters(Object obj, Store<EntityStore> store) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!Ref.class.isAssignableFrom(m.getReturnType())) continue;

                Object v;
                try { v = m.invoke(obj); } catch (Throwable ignored) { continue; }
                if (!(v instanceof Ref<?> rr)) continue;

                Ref<EntityStore> ref;
                try { ref = (Ref<EntityStore>) rr; } catch (Throwable ignored) { continue; }

                if (ref == null || !ref.isValid()) continue;

                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) return p.getUuid();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private UUID scanNoArgPlayerRefGetters(Object obj) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!PlayerRef.class.isAssignableFrom(m.getReturnType())) continue;

                Object v;
                try { v = m.invoke(obj); } catch (Throwable ignored) { continue; }
                if (v instanceof PlayerRef pr && pr.isValid()) return pr.getUuid();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private UUID scanNoArgUuidGetters(Object obj) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!UUID.class.isAssignableFrom(m.getReturnType())) continue;

                Object v;
                try { v = m.invoke(obj); } catch (Throwable ignored) { continue; }
                if (v instanceof UUID id) return id;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object scanNoArgNonJavaObject(Object obj) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;

                Class<?> rt = m.getReturnType();
                if (rt.isPrimitive()) continue;
                if (rt.getName().startsWith("java.")) continue;
                if (rt.isEnum()) continue;
                if (rt.getName().startsWith("com.hypixel.hytale.component.Ref")) continue;
                if (rt.getName().startsWith("com.hypixel.hytale.server.core.universe.PlayerRef")) continue;

                String n = m.getName().toLowerCase();
                if (!(n.contains("entity") || n.contains("source") || n.contains("owner") || n.contains("attacker") || n.contains("instigator"))) continue;

                Object v;
                try { v = m.invoke(obj); } catch (Throwable ignored) { continue; }
                if (v != null) return v;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void dumpDamage(Damage damage) {
        try {
            HytaleLogger.getLogger().atInfo().log("[DMG-DUMP] class=%s", damage.getClass().getName());

            for (Method m : damage.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;

                Class<?> rt = m.getReturnType();
                String name = m.getName();

                // garde seulement les getters plausibles
                String n = name.toLowerCase();
                if (!(n.startsWith("get") || n.startsWith("is"))) continue;

                // types intéressants
                boolean interesting =
                        com.hypixel.hytale.component.Ref.class.isAssignableFrom(rt) ||
                                com.hypixel.hytale.server.core.universe.PlayerRef.class.isAssignableFrom(rt) ||
                                java.util.UUID.class.isAssignableFrom(rt) ||
                                rt == int.class || rt == long.class ||
                                n.contains("att") || n.contains("source") || n.contains("instig") || n.contains("owner") || n.contains("cause");

                if (!interesting) continue;

                Object v = null;
                try { v = m.invoke(damage); } catch (Throwable ignored) {}

                String vt = (v == null) ? "null" : v.getClass().getName();
                String vs = (v == null) ? "null" : String.valueOf(v);

                HytaleLogger.getLogger().atInfo().log("[DMG-DUMP] %s -> %s | %s | %s", name, rt.getName(), vt, vs);
            }
        } catch (Throwable t) {
            HytaleLogger.getLogger().atWarning().log("[DMG-DUMP] failed: %s", t.toString());
        }
    }
}
