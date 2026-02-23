package com.lostale.hylostale.service.player;

import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.player.HylPlayerManager;

import java.util.UUID;

public class HylPlayerStatsService {
    
    private final HylPlayerManager mgr;
    private final HylConfig cfg;

    public HylPlayerStatsService(HylPlayerManager mgr, HylConfig cfg) {
        this.mgr = mgr;
        this.cfg = cfg;
    }

    // --- Calculs dérivés ---
    public int computeMaxHp(int level) {
        int lvl = Math.max(1, level);
        return Math.max(1, cfg.baseHp + (lvl - 1) * cfg.hpPerLevel);
    }

    public int computeMaxMana(int level) {
        int lvl = Math.max(1, level);
        return Math.max(0, cfg.baseMana + (lvl - 1) * cfg.manaPerLevel);
    }

    /** Recalcule maxHp/maxMana depuis le level, clamp hp/mana. */
    public HylPlayerData recompute(UUID uuid) {
        HylPlayerData d = mgr.get(uuid);

        d.maxHp = computeMaxHp(d.level);
        d.maxMana = computeMaxMana(d.level);

        if (d.hp <= 0) d.hp = Math.min(d.hp, d.maxHp); // laisse mort si <=0
        else d.hp = Math.min(d.hp, d.maxHp);

        d.mana = Math.min(Math.max(0, d.mana), d.maxMana);

        d.clamp();
        mgr.save(uuid);
        return d;
    }

    // --- HP ---
    public HylPlayerData healToFull(UUID uuid) {
        HylPlayerData d = mgr.get(uuid);
        d.hp = d.maxHp;
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData heal(UUID uuid, int amount) {
        HylPlayerData d = mgr.get(uuid);
        int a = Math.max(0, amount);
        d.hp = Math.min(d.maxHp, d.hp + a);
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData damage(UUID uuid, int amount) {
        HylPlayerData d = mgr.get(uuid);
        int a = Math.max(0, amount);
        d.hp = Math.max(0, d.hp - a);
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData setHp(UUID uuid, int hp) {
        HylPlayerData d = mgr.get(uuid);
        d.hp = Math.max(0, Math.min(d.maxHp, hp));
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    // --- Mana ---
    public HylPlayerData refillMana(UUID uuid) {
        HylPlayerData d = mgr.get(uuid);
        d.mana = d.maxMana;
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData gainMana(UUID uuid, int amount) {
        HylPlayerData d = mgr.get(uuid);
        int a = Math.max(0, amount);
        d.mana = Math.min(d.maxMana, d.mana + a);
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData spendMana(UUID uuid, int amount) {
        HylPlayerData d = mgr.get(uuid);
        int a = Math.max(0, amount);
        d.mana = Math.max(0, d.mana - a);
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    public HylPlayerData setMana(UUID uuid, int mana) {
        HylPlayerData d = mgr.get(uuid);
        d.mana = Math.max(0, Math.min(d.maxMana, mana));
        d.clamp();
        mgr.save(uuid);
        return d;
    }

    // --- Combat flag ---
    public void markCombat(UUID uuid) {
        HylPlayerData d = mgr.get(uuid);
        long now = System.currentTimeMillis();
        long until = now + Math.max(0L, cfg.combatKeepMs);
        if (until > d.combatUntilMs) d.combatUntilMs = until;
        mgr.save(uuid);
    }

    public boolean isInCombat(UUID uuid) {
        HylPlayerData d = mgr.get(uuid);
        return System.currentTimeMillis() < d.combatUntilMs;
    }
}
