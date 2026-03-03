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

    private final ConcurrentHashMap<UUID, Double> hpCarry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> manaCarry = new ConcurrentHashMap<>();

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
        long periodMs = Math.max(50L, cfg.regenHpIntervalMs);
        double dt = periodMs / 1000.0;

        for (UUID id : online) {
            if (stats.isInCombat(id)) { hpCarry.remove(id); continue; }

            HylPlayerData d = mgr.peek(id);
            if (d == null) continue;

            if (d.hp <= 0) { hpCarry.remove(id); continue; }
            if (d.hp >= d.maxHp) { hpCarry.put(id, 0.0); continue; }

            // compute regen/sec depuis tes stats (à implémenter dans stats.compute(d))
            HylPlayerData.ComputedStats cs = stats.compute(d);

            double carry = hpCarry.getOrDefault(id, 0.0);
            double amount = carry + (cs.hpRegenPerSec() * dt);

            int add = (int) Math.floor(amount);
            hpCarry.put(id, amount - add);

            if (add <= 0) continue;

            stats.heal(id, add);
            huds.renderHealth(id);
        }
    }

    private void tickMana() {
        long periodMs = Math.max(50L, cfg.regenManaIntervalMs);
        double dt = periodMs / 1000.0;

        for (UUID id : online) {
            if (stats.isInCombat(id)) { manaCarry.remove(id); continue; }

            HylPlayerData d = mgr.peek(id);
            if (d == null) continue;

            if (d.mana <= 0) { manaCarry.remove(id); continue; }
            if (d.mana >= d.maxMana) { manaCarry.put(id, 0.0); continue; }

            // compute regen/sec depuis tes stats (à implémenter dans stats.compute(d))
            HylPlayerData.ComputedStats cs = stats.compute(d);

            double carry = manaCarry.getOrDefault(id, 0.0);
            double amount = carry + (cs.manaRegenPerSec() * dt);

            int add = (int) Math.floor(amount);
            manaCarry.put(id, amount - add);

            if (add <= 0) continue;

            stats.gainMana(id, add);
            huds.renderMana(id);
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