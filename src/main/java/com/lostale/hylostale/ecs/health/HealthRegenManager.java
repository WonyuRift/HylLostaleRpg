package com.lostale.hylostale.ecs.health;


import com.lostale.hylostale.ui.HylHudManager;
import com.lostale.hylostale.services.hud.HylHudService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class HealthRegenManager {

    private final HylHudService stats;
    private final HylHudManager huds;

    // réglages
    private final long periodMs = 100;              // 10 Hz -> fluide
    private final long outOfCombatDelayMs = 5000;   // 5s sans dégâts
    private final double hpPerSecond = 2.5;         // 2.5 HP/s (ajuste)

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "lostale-hp-regen");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> task;

    private final Map<UUID, Long> lastCombatMs = new ConcurrentHashMap<>();
    private final Map<UUID, Double> frac = new ConcurrentHashMap<>();

    public HealthRegenManager(HylHudService stats, HylHudManager huds) {
        this.stats = stats;
        this.huds = huds;
    }

    public void start() {
        stop();
        task = exec.scheduleAtFixedRate(this::tick, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (task != null) task.cancel(false);
        task = null;
    }

    public void shutdown() {
        stop();
        exec.shutdownNow();
        lastCombatMs.clear();
        frac.clear();
    }

    /** Appeler quand le joueur subit (ou inflige) des dégâts. */
    public void markInCombat(UUID id) {
        lastCombatMs.put(id, System.currentTimeMillis());
        frac.remove(id);
    }

    public boolean isInCombat(UUID playerId) {
        Long until = lastCombatMs.get(playerId);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            lastCombatMs.remove(playerId);
            frac.remove(playerId);
            return false;
        }
        return true;

    }

    /** Appeler au disconnect pour éviter fuite mémoire. */
    public void remove(UUID id) {
        lastCombatMs.remove(id);
        frac.remove(id);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (UUID id : huds.getOnlineIds()) {
            HylHudService.PlayerData st = stats.ensure(id);

            // déjà full
            if (st.hp() >= st.maxHp()) {
                frac.remove(id);
                continue;
            }

            long last = lastCombatMs.getOrDefault(id, 0L);
            if (now - last < outOfCombatDelayMs) continue; // encore en combat

            // regen fractionnaire
            double add = hpPerSecond * (periodMs / 1000.0);
            double acc = frac.getOrDefault(id, 0.0) + add;

            int whole = (int) Math.floor(acc);
            if (whole <= 0) {
                frac.put(id, acc);
                continue;
            }

            acc -= whole;
            frac.put(id, acc);

            stats.heal(id, whole);
            huds.renderHealth(id);
        }
    }
}