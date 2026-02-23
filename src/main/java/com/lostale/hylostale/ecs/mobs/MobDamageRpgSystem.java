package com.lostale.hylostale.ecs.mobs;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.ecs.health.HealthRegenManager;
import com.lostale.hylostale.services.hud.HylHudService;
import com.lostale.hylostale.services.mobs.MobLevelService;
import com.lostale.hylostale.ui.HylHudManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobDamageRpgSystem extends DamageEventSystem {

    private final MobLevelService mobRpg;
    private final HealthRegenManager combat;
    private final HylHudService playerRpg;
    private final HylHudManager huds;
    private final Set<Ref<EntityStore>> rewarded = ConcurrentHashMap.newKeySet(); // anti double xp reward
    private final Map<Ref<EntityStore>, UUID> lastDamager = new ConcurrentHashMap<>();

    public MobDamageRpgSystem(MobLevelService mobRpg, HylHudManager huds, HealthRegenManager combat, HylHudService playerRpg) {
        this.mobRpg = mobRpg;
        this.huds = huds;
        this.combat = combat;
        this.playerRpg = playerRpg;
    }

    @Override public @Nonnull Query<EntityStore> getQuery() { return Query.any(); }

    @Override public @Nonnull SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

        // ignore joueurs
        Player p = store.getComponent(targetRef, Player.getComponentType());
        if (p != null) return;

        if (!mobRpg.has(targetRef)) return;

        damage.setCancelled(true);

        int amount = (int) Math.ceil(damage.getAmount());
        MobLevelService.MobState st = mobRpg.damage(targetRef, amount);
        if (st == null) return;

        if (st.hp() > 0) {
            damage.setCancelled(true);
            return;
        }

        UUID attacker = tryGetAttackerPlayerUuid(damage, store);
        if (attacker != null) {
            lastDamager.put(targetRef, attacker);
        }

        // XP reward une seule fois
        if (rewarded.add(targetRef)) {
                UUID killer = lastDamager.get(targetRef); // source fiable
                if (killer != null) {
                    int playerLevel = playerRpg.ensure(killer).level();
                    int mobLevel = st.level();

                    int xp = computeKillXp(playerLevel, mobLevel);

                    playerRpg.addXp(killer, xp);
                    huds.renderXp(killer);
                }
                // cleanup
                lastDamager.remove(targetRef);
        }

        damage.setCancelled(false);
        damage.setAmount(999999f);
        mobRpg.remove(targetRef);
    }

    private int computeKillXp(int playerLevel, int mobLevel) {
        int base = 20;
        double k = 0.08;
        double min = 0.25, max = 5.0;

        int diff = mobLevel - playerLevel;
        double mult = 1.0 + diff * k;
        mult = Math.max(min, Math.min(max, mult));

        return (int) Math.max(1, Math.round(base * mult));
    }

    @SuppressWarnings("unchecked")
    private UUID tryGetAttackerPlayerUuid(Damage damage, Store<EntityStore> store) {

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
