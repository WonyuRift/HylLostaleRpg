package com.lostale.hylostale.utils.math;

import com.lostale.hylostale.config.HylConfig;

public class HylDamageScaling {

    private HylDamageScaling() {}

    public static int scale(HylConfig cfg,
                            int rawDamage,
                            int attackerLevel,
                            int attackerAtk,
                            int targetLevel,
                            int targetDef) {

        int raw = Math.max(0, rawDamage);

        // 1) Base RPG (domine)
        double base = attackerAtk;

        // 2) Ajout léger du vanilla (optionnel)
        base += raw * 0.25; // 25% du vanilla seulement

        // 3) Diff level (cap)
        double mult = 1.0 + (attackerLevel - targetLevel) * cfg.dmgDiffPerLevel;
        if (mult < cfg.dmgDiffMinMult) mult = cfg.dmgDiffMinMult;
        if (mult > cfg.dmgDiffMaxMult) mult = cfg.dmgDiffMaxMult;

        double dmg = base * mult;

        // 4) Réduction def (douce)
        double defK = Math.max(1.0, cfg.defK);
        double reduce = defK / (defK + Math.max(0, targetDef));
        dmg *= reduce;

        int out = (int)Math.round(dmg);
        return Math.max(1, out);
    }
}
