package com.lostale.hylostale.data.player;

import java.util.UUID;

public class HylPlayerData {

    // --- Identité ---
    public final UUID uuid;

    // --- Progression ---
    public int level;     // >= 1
    public int xp;        // >= 0 (xp courant dans le niveau)

    public int statPoints;   // points à dépenser
    public int spentStr;
    public int spentDex;
    public int spentInt;
    public int spentCon;
    public int spentCha;
    public int spentSen;

    // --- Stats ---
    public int maxHp;     // >= 1
    public int hp;        // [0..maxHp]

    public int maxMana;   // >= 0
    public int mana;      // [0..maxMana]

    public int str; // Strength
    public int dex; // Dexterity
    public int intel; // Intelligence
    public int con; // Concentration
    public int cha; // Charm
    public int sen; // Sensibility


    // --- Runtime combat/regen (optionnel DB) ---
    public long combatUntilMs; // 0 = hors combat

    public HylPlayerData(UUID uuid) {
        this.uuid = uuid;
        this.level = 1;
        this.xp = 0;
        this.maxHp = 100;
        this.hp = 100;
        this.maxMana = 0;
        this.mana = 0;
        this.combatUntilMs = 0L;
    }

    public static HylPlayerData defaults(UUID uuid, int baseHp, int baseMana) {
        HylPlayerData d = new HylPlayerData(uuid);
        d.maxHp = Math.max(1, baseHp);
        d.hp = d.maxHp;
        d.maxMana = Math.max(0, baseMana);
        d.mana = d.maxMana;
        return d;
    }

    public void clamp() {
        if (level < 1) level = 1;
        if (xp < 0) xp = 0;

        if (maxHp < 1) maxHp = 1;
        if (hp < 0) hp = 0;
        if (hp > maxHp) hp = maxHp;

        if (maxMana < 0) maxMana = 0;
        if (mana < 0) mana = 0;
        if (mana > maxMana) mana = maxMana;

        if (combatUntilMs < 0) combatUntilMs = 0L;

        str = Math.max(1, str);
        dex = Math.max(1, dex);
        intel = Math.max(1, intel);
        con = Math.max(1, con);
        cha = Math.max(1, cha);
        sen = Math.max(1, sen);
    }

    public record ComputedStats(
            int hpMax,
            int manaMax,
            double ap,
            double def,
            double mdef,
            double hitRate,
            double dodgeChance,
            double critChance,
            double critMult,
            double moveSpeedMult,
            double atkSpeedMult,
            double hpRegenPerSec,
            double manaRegenPerSec
    ) {}

    public enum WeaponFamily {
        MELEE_STR,
        DUAL_DEX,
        RANGED_DEX_SEN,
        MAGIC_INT_SEN,
        GUN_CON_SEN
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean isInCombat(long nowMs) {
        return nowMs < combatUntilMs;
    }

    public void markCombat(long nowMs, long keepMs) {
        long until = nowMs + Math.max(0L, keepMs);
        if (until > combatUntilMs) combatUntilMs = until;
    }

    public void healToFull() {
        hp = maxHp;
    }

    public void damage(int amount) {
        hp = Math.max(0, hp - Math.max(0, amount));
    }

    public void heal(int amount) {
        hp = Math.min(maxHp, hp + Math.max(0, amount));
    }

    public void spendMana(int amount) {
        mana = Math.max(0, mana - Math.max(0, amount));
    }

    public void gainMana(int amount) {
        mana = Math.min(maxMana, mana + Math.max(0, amount));
    }
}
