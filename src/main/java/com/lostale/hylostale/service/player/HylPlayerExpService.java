package com.lostale.hylostale.service.player;

import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.player.HylPlayerManager;

import java.util.UUID;

public class HylPlayerExpService {
    
    private final HylPlayerManager mgr;
    private final HylConfig cfg;

    public HylPlayerExpService(HylPlayerManager mgr, HylConfig cfg) {
        this.mgr = mgr;
        this.cfg = cfg;
    }

    /** XP nécessaire pour passer du level -> level+1 */
    public int xpToNext(int level) {
        int lvl = Math.max(1, level);

        // courbe: xpBase * level^xpExponent
        double v = cfg.xpBase * Math.pow(lvl, cfg.xpExponent);

        // sécurité
        int out = (int) Math.round(v);
        return Math.max(1, out);
    }

    /**
     * Ajoute de l'XP. Retourne true si level up (au moins 1).
     * Ne heal pas, ne recalcul pas HP: à faire via StatsService après.
     */
    public boolean addXp(UUID uuid, int amount) {
        HylPlayerData d = mgr.get(uuid);

        int add = Math.max(0, amount);
        if (add == 0) return false;

        int beforeLevel = d.level;

        int xp = d.xp + add;
        int level = d.level;

        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
        }

        d.level = Math.max(1, level);
        d.xp = Math.max(0, xp);
        d.clamp();

        mgr.save(uuid);

        return d.level > beforeLevel;
    }

    /** Set level (>=1) en gardant l'xp courant clampé au seuil. */
    public void setLevel(UUID uuid, int level) {
        HylPlayerData d = mgr.get(uuid);

        d.level = Math.max(1, level);

        int threshold = xpToNext(d.level);
        d.xp = Math.max(0, Math.min(d.xp, threshold - 1));

        d.clamp();
        mgr.save(uuid);
    }

    /** Set xp courant dans le niveau [0..xpToNext(level)-1] */
    public void setXp(UUID uuid, int xp) {
        HylPlayerData d = mgr.get(uuid);

        int threshold = xpToNext(d.level);
        d.xp = Math.max(0, Math.min(xp, threshold - 1));

        d.clamp();
        mgr.save(uuid);
    }

    /** Format utilitaire pour HUD/debug. */
    public String format(HylPlayerData d) {
        return "Niveau: " + d.level + " | XP: " + d.xp + "/" + xpToNext(d.level);
    }
}
