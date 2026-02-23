package com.lostale.hylostale.service.mob;

import com.lostale.hylostale.config.HylConfig;

public class HylMobXpService {
    private final HylConfig cfg;

    public HylMobXpService(HylConfig cfg) {
        this.cfg = cfg;
    }

    public int computeKillXp(int playerLevel, int mobLevel) {
        int base = cfg.mobXpBase;
        int diff = mobLevel - playerLevel;

        double mult = 1.0 + diff * cfg.mobXpPerLevelDiff;
        mult = Math.max(cfg.mobXpMinMultiplier, Math.min(cfg.mobXpMaxMultiplier, mult));

        return (int) Math.max(1, Math.round(base * mult));
    }
}
