package com.lostale.hylostale.entity.player.systems;


import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public final class HylRegenManager implements AutoCloseable {

    private final HylPlayerManager mgr;
    private final HylPlayerStatsService stats;
    private final HylHudService huds;
    private final HylConfig cfg;

    private final ScheduledExecutorService exec;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();

    private ScheduledFuture<?> hpTask;
    private ScheduledFuture<?> manaTask;

    public HylRegenManager(@Nonnull HylPlayerManager mgr,
                           @Nonnull HylPlayerStatsService stats,
                           @Nonnull HylHudService huds,
                           @Nonnull HylConfig cfg) {
        this.mgr = mgr;
        this.stats = stats;
        this.huds = huds;
        this.cfg = cfg;

        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lostale-regeneration");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Démarre les ticks de regen (à appeler une fois dans setup()).
     */
    public void start() {
        stop();

        long hpPeriod = Math.max(50L, cfg.regenHpIntervalMs);
        long manaPeriod = Math.max(50L, cfg.regenManaIntervalMs);

        hpTask = exec.scheduleAtFixedRate(this::tickHpSafe, hpPeriod, hpPeriod, TimeUnit.MILLISECONDS);
        manaTask = exec.scheduleAtFixedRate(this::tickManaSafe, manaPeriod, manaPeriod, TimeUnit.MILLISECONDS);
    }

    /**
     * Stoppe les ticks (safe).
     */
    public void stop() {
        if (hpTask != null) {
            hpTask.cancel(false);
            hpTask = null;
        }
        if (manaTask != null) {
            manaTask.cancel(false);
            manaTask = null;
        }
    }

    /**
     * Appelé quand un joueur devient "actif" (PlayerReadyEvent).
     */
    public void onPlayerReady(@Nonnull UUID uuid) {
        online.add(uuid);
    }

    /**
     * Appelé à la déconnexion.
     */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        online.remove(uuid);
    }

    /**
     * Marque un joueur en combat (alias pratique si tu veux centraliser).
     * Normalement appelé dans tes systèmes de dégâts.
     */
    public void markCombat(@Nonnull UUID uuid) {
        stats.markCombat(uuid);
    }

    // -----------------------
    // Internal tick methods
    // -----------------------

    private void tickHpSafe() {
        try {
            tickHp();
        } catch (Throwable ignored) {
        }
    }

    private void tickManaSafe() {
        try {
            tickMana();
        } catch (Throwable ignored) {
        }
    }

    private void tickHp() {
        int amount = Math.max(0, cfg.regenHpAmount);
        if (amount == 0) return;

        for (UUID id : online) {
            if (stats.isInCombat(id)) continue;

            HylPlayerData d = mgr.peek(id);
            if (d == null) continue;

            if (d.hp <= 0) continue;           // pas de regen si mort
            if (d.hp >= d.maxHp) continue;     // déjà plein

            stats.heal(id, amount);
            huds.renderHealth(id);
        }
    }

    private void tickMana() {
        int amount = Math.max(0, cfg.regenManaAmount);
        if (amount == 0) return;

        for (UUID id : online) {
            if (stats.isInCombat(id)) continue;

            HylPlayerData d = mgr.peek(id);
            if (d == null) continue;

            if (d.maxMana <= 0) continue;
            if (d.mana >= d.maxMana) continue;

            stats.gainMana(id, amount);
            huds.renderMana(id); // si tu n'as pas encore renderMana, remplace par renderAll ou retire
        }
    }

    /**
     * Ferme proprement le manager.
     */
    @Override
    public void close() {
        stop();
        exec.shutdownNow();
    }

    // -----------------------
    // Compat helpers (optionnel)
    // -----------------------

    /**
     * Si tu n'as pas encore câblé le render mana, appelle ceci à la place.
     */
    @SuppressWarnings("unused")
    private void renderFallback(@NonNullDecl UUID id) {
        // huds.renderAll(id);
    }
}