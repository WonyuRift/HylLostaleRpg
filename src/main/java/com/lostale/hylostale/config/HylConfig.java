package com.lostale.hylostale.config;

public class HylConfig {

    // =========================
    // === BASE PLAYER STATS ===
    // =========================

    public int baseHp = 100;
    public int hpPerLevel = 8;

    public int baseMana = 50;
    public int manaPerLevel = 4;

    // =====================
    // === XP PROGRESSION ==
    // =====================

    /**
     * Formule: xpBase * level^xpExponent
     */
    public int xpBase = 80;
    public double xpExponent = 1.35;

    // ============================
    // === MOB XP REWARD SCALING ==
    // ============================

    /**
     * XP donnée par un mob avant multiplicateur de diff de niveau.
     */
    public int mobXpBase = 20;

    /**
     * +X% par différence de niveau.
     * Exemple 0.08 = +8% par niveau d'écart.
     */
    public double mobXpPerLevelDiff = 0.08;

    /**
     * Multiplicateur minimum (si joueur beaucoup plus haut niveau).
     */
    public double mobXpMinMultiplier = 0.25;

    /**
     * Multiplicateur maximum (si mob beaucoup plus haut niveau).
     */
    public double mobXpMaxMultiplier = 5.0;

    // ===================
    // === COMBAT STATE ==
    // ===================

    /**
     * Temps en ms pendant lequel le joueur reste "en combat".
     */
    public long combatKeepMs = 6000L;

    // ==================
    // === REGENERATION ==
    // ==================

    /**
     * Régénération HP par tick (ou seconde selon ton implémentation).
     */
    public int regenHpAmount = 2;

    /**
     * Intervalle regen HP (ms).
     */
    public long regenHpIntervalMs = 1000L;

    /**
     * Régénération mana.
     */
    public int regenManaAmount = 3;
    public long regenManaIntervalMs = 800L;

    // =========================
    // === BASE MOBS STATS ===
    // =========================

    public int mobBaseHp = 50;
    public int mobHpPerLevel = 8;
}
