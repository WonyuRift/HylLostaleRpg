package com.lostale.hylostale.service.player;

import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.player.HylPlayerManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class HylPlayerStatsService {
    
    private final HylPlayerManager mgr;
    private final HylConfig cfg;

    public HylPlayerStatsService(HylPlayerManager mgr, HylConfig cfg) {
        this.mgr = mgr;
        this.cfg = cfg;
    }

    // ----------------------------
    // Max recompute (level scaling)
    // ----------------------------

    public HylPlayerData.ComputedStats compute(@Nonnull HylPlayerData d) {
        return compute(d, HylPlayerData.WeaponFamily.MELEE_STR);
    }

    public HylPlayerData.ComputedStats compute(HylPlayerData d, HylPlayerData.WeaponFamily weapon) {
        int lvl = Math.max(1, d.level);

        int baseHp = 100 + (lvl - 1) * 10;
        int baseMana = 50 + (lvl - 1) * 6;
        int baseAp = 10 + (lvl - 1) * 2;
        int baseDef = 5 + (lvl - 1) * 1;
        int baseMDef = 3 + (lvl - 1) * 1;

        int hpMax = Math.round(baseHp + d.str * 5 + d.con * 2);
        int manaMax = Math.round(baseMana + d.intel * 6 + d.con * 1);

        double def = baseDef + d.str * 1.0;
        double mdef = baseMDef + d.intel * 1.0;

        double hitRate = clamp(0.70 + d.con * 0.005, 0.70, 0.98);
        double dodge = clamp(d.dex * 0.003, 0.0, 0.30);
        double crit = clamp(d.sen * 0.0025, 0.0, 0.35);
        double critMult = 2.0 + clamp(d.sen * 0.002, 0.0, 0.5);

        double moveSpd = clamp(1.0 + d.dex * 0.002, 1.0, 1.12);
        double atkSpd = clamp(1.0 + d.dex * 0.0015 + d.con * 0.001, 1.0, 1.20);

        double hpRegen = 0.5 + d.con * 0.05;
        double manaRegen = 0.3 + d.con * 0.03 + d.intel * 0.02;

        double ap = switch (weapon) {
            case MELEE_STR -> baseAp + d.str * 2.0 + d.dex * 0.5;
            case DUAL_DEX -> baseAp + d.dex * 2.0 + d.str * 0.5;
            case RANGED_DEX_SEN -> baseAp + d.dex * 1.5 + d.sen * 1.0;
            case MAGIC_INT_SEN -> baseAp + d.intel * 2.0 + d.sen * 0.5;
            case GUN_CON_SEN -> baseAp + d.con * 1.8 + d.sen * 0.7;
        };

        return new HylPlayerData.ComputedStats(hpMax, manaMax, ap, def, mdef, hitRate, dodge, crit, critMult, moveSpd, atkSpd, hpRegen, manaRegen);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Recalcule maxHp/maxMana depuis le niveau, clamp hp/mana, save si changement.
     */
    public void applyCaps(HylPlayerData d, HylPlayerData.WeaponFamily weapon, boolean healToFull) {
        HylPlayerData.ComputedStats s = compute(d, weapon);

        int oldMaxHp = d.maxHp;
        int oldMaxMana = d.maxMana;

        d.maxHp = s.hpMax();
        d.maxMana = s.manaMax();

        if (healToFull && d.maxHp > oldMaxHp) {
            d.hp = d.maxHp;
        } else {
            if (d.hp > 0) d.hp = Math.min(d.hp, d.maxHp);
            else d.hp = 0;
        }

        d.mana = Math.min(Math.max(0, d.mana), d.maxMana);

        d.clamp();
    }


    public HylPlayerData recompute(UUID uuid, boolean healToFull) {
        HylPlayerData d = mgr.get(uuid);

        // Tant que tu n’as pas “arme équipée”, mets un défaut.
        HylPlayerData.WeaponFamily weapon = HylPlayerData.WeaponFamily.MELEE_STR;

        applyCaps(d, weapon, healToFull);

        mgr.save(uuid);
        return d;
    }

    // ----------------------------
    // HP operations
    // ----------------------------

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

    // ----------------------------
    // Mana operations
    // ----------------------------

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

    // ----------------------------
    // Combat state
    // ----------------------------
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

    // ----------------------------
    // Attack / Defense (scaling dégâts)
    // ----------------------------


    public int attack(@Nonnull UUID id) {
        int lvl = Math.max(1, mgr.get(id).level);
        int base = cfg.playerBaseAtk;
        int per = cfg.playerAtkPerLevel;
        int atk = base + (lvl - 1) * per;
        return Math.max(0, atk);
    }

    public int defense(@Nonnull UUID id) {
        int lvl = Math.max(1, mgr.get(id).level);
        int base = cfg.playerBaseDef;
        int per = cfg.playerDefPerLevel;
        int def = base + (lvl - 1) * per;
        return Math.max(0, def);
    }

    // ----------------------------
    // Accessors utiles
    // ----------------------------
    public int level(@Nonnull UUID id) {
        return mgr.get(id).level;
    }

    public int hp(@Nonnull UUID id) {
        return mgr.get(id).hp;
    }

    public int maxHp(@Nonnull UUID id) {
        return mgr.get(id).maxHp;
    }

    public int mana(@Nonnull UUID id) {
        return mgr.get(id).mana;
    }

    public int maxMana(@Nonnull UUID id) {
        return mgr.get(id).maxMana;
    }

}
