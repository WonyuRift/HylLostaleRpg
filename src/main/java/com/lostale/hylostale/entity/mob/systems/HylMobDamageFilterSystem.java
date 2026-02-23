package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;
import com.lostale.hylostale.service.mob.HylMobXpService;
import com.lostale.hylostale.service.player.HylPlayerExpService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;

import javax.annotation.Nonnull;
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

    private final Map<Ref<EntityStore>, UUID> lastDamager = new ConcurrentHashMap<>();

    public HylMobDamageFilterSystem(@Nonnull HylMobManager mobs,
                                    @Nonnull HylMobStatsService mobStats,
                                    @Nonnull HylMobXpService mobXp,
                                    @Nonnull HylPlayerManager players,
                                    @Nonnull HylPlayerExpService xp,
                                    @Nonnull HylPlayerStatsService playerStats,
                                    @Nonnull HylHudService huds) {
        this.mobs = mobs;
        this.mobStats = mobStats;
        this.mobXp = mobXp;
        this.players = players;
        this.xp = xp;
        this.playerStats = playerStats;
        this.huds = huds;
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

        float raw = damage.getAmount();
        int amount = (int) Math.ceil(Math.max(0f, raw));
        if (amount <= 0) return;

        // Annule dégâts vanilla et applique dégâts RPG
        damage.setCancelled(true);
        mobStats.damage(mob, amount);

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
                boolean leveled = xp.addXp(killer, gained);
                int after = players.get(killer).level;

                if (after > before || leveled) {
                    playerStats.recompute(killer);
                    playerStats.healToFull(killer);
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

        // getAttacker() -> Ref -> Player
        try {
            Object refObj = damage.getClass().getMethod("getAttacker").invoke(damage);
            if (refObj instanceof Ref<?> rr) {
                Ref<EntityStore> ref = (Ref<EntityStore>) rr;
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) return p.getUuid();
            }
        } catch (Throwable ignored) {}

        // getSource() -> Ref -> Player
        try {
            Object refObj = damage.getClass().getMethod("getSource").invoke(damage);
            if (refObj instanceof Ref<?> rr) {
                Ref<EntityStore> ref = (Ref<EntityStore>) rr;
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) return p.getUuid();
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
