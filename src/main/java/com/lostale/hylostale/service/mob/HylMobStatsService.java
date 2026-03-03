package com.lostale.hylostale.service.mob;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.mob.HylMobData;
import com.lostale.hylostale.entity.mob.HylMobManager;

public class HylMobStatsService {

    private final HylMobManager mobs;
    private final HylConfig cfg;

    public HylMobStatsService(HylMobManager mobs, HylConfig cfg) {
        this.mobs = mobs;
        this.cfg = cfg;
    }

    public int computeMaxHp(int level) {
        int lvl = Math.max(1, level);
        // exemple: baseMobHp + (lvl-1)*mobHpPerLevel
        int base = Math.max(1, cfg.mobBaseHp);
        int per = Math.max(0, cfg.mobHpPerLevel);
        return Math.max(1, base + (lvl - 1) * per);
    }

    public void initMob(HylMobData d, int level, boolean hostile, String name) {
        d.level = Math.max(1, level);
        d.hostile = hostile;
        if (name != null && !name.isBlank()) d.name = name;

        d.maxHp = computeMaxHp(d.level);
        d.hp = d.maxHp;

        d.initialized = true;
        d.rewarded = false;
        d.clamp();
    }

    public void damage(HylMobData d, int amount) {
        int a = Math.max(0, amount);
        d.hp = Math.max(0, d.hp - a);
        d.clamp();
    }

    public void heal(HylMobData d, int amount) {
        int a = Math.max(0, amount);
        d.hp = Math.min(d.maxHp, d.hp + a);
        d.clamp();
    }

    public int attack(int level) {
        int lvl = Math.max(1, level);
        return cfg.mobBaseAtk + (lvl - 1) * cfg.mobAtkPerLevel;
    }

    public int defense(int level) {
        int lvl = Math.max(1, level);
        return cfg.mobBaseDef + (lvl - 1) * cfg.mobDefPerLevel;
    }
}
