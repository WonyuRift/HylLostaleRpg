package com.lostale.hylostale.entity.player.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;
import com.lostale.hylostale.utils.math.HylDamageScaling;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.UUID;

public class HylDamagePlayerFilter extends DamageEventSystem {

    private final HylPlayerStatsService stats;
    private final HylHudService huds;
    private final HylMobManager mobManager;
    private final HylMobStatsService mobStats;
    private final HylConfig config;
    private final HylPlayerManager playerManager;

    public HylDamagePlayerFilter(@Nonnull HylPlayerStatsService stats, @Nonnull HylHudService huds, HylMobManager mobManager, HylMobStatsService mobStats, HylConfig config, HylPlayerManager playerManager) {
        this.stats = stats;
        this.huds = huds;
        this.mobManager = mobManager;
        this.mobStats = mobStats;
        this.config = config;
        this.playerManager = playerManager;
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

        Player target = store.getComponent(targetRef, Player.getComponentType());
        if (target == null) return; // uniquement joueurs

        // Annule le vanilla
        damage.setCancelled(true);

        // Raw
        int raw = (int) Math.ceil(Math.max(0.0f, safeAmount(damage)));
        if (raw <= 0) return;

        UUID pid;
        try { pid = target.getUuid(); }
        catch (Throwable t) { return; }

        // Data joueur (assure existence)
        HylPlayerData pd = playerManager.get(pid);

        // Attaquant (mob)
        int attackerLvl = 1;
        int attackerAtk = 0;

        Ref<EntityStore> srcRef = tryGetSourceEntityRef(damage);
        if (srcRef != null) {
            HylMobData md = mobManager.peek(srcRef);
            if (md != null && md.initialized) {
                attackerLvl = md.level;
                attackerAtk = mobStats.attack(md.level);
            }
        }

        // DEF calculée (nouveau système)
        // Tant que tu n'as pas "arme équipée" pour le joueur, family fixe.
        HylPlayerData.ComputedStats cs = stats.compute(pd);
        int targetLvl = pd.level;
        int targetDef = (int) Math.round(Math.max(0.0, cs.def()));

        int scaled = HylDamageScaling.scale(
                config, raw,
                attackerLvl, attackerAtk,
                targetLvl, targetDef
        );

        if (scaled <= 0) scaled = 1;

        // Combat flag (regen off etc.)
        stats.markCombat(pid);

        // Applique dégâts RPG
        HylPlayerData after = stats.damage(pid, scaled);

        // HUD
        huds.renderHealth(pid);

        // Mort RPG minimale: reset full HP (tu remplaceras par respawn/teleport)
        if (pd != null && pd.hp <= 0) {
            stats.healToFull(pid);
            huds.renderHealth(pid);
        }
    }

    private static float safeAmount(Damage damage) {
        try {
            float a = damage.getAmount();
            if (Float.isNaN(a) || Float.isInfinite(a)) return 0f;
            return a;
        } catch (Throwable ignored) {
            return 0f;
        }
    }
    @SuppressWarnings("unchecked")
    private Ref<EntityStore> tryGetSourceEntityRef(Damage damage) {
        try {
            Object src = damage.getSource();
            if (src == null) return null;

            for (var m : src.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!com.hypixel.hytale.component.Ref.class.isAssignableFrom(m.getReturnType())) continue;

                Object v = m.invoke(src);
                if (v instanceof Ref<?> rr) {
                    Ref<EntityStore> ref = (Ref<EntityStore>) rr;
                    if (ref != null && ref.isValid()) return ref;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // Optionnel: utilitaire pour clear/unregister, pas utilisé ici
    public void onPlayerDisconnect(@NonNullDecl UUID uuid) {
        // rien à faire: data gérée par PlayerRpgManager.unload()
    }
}
