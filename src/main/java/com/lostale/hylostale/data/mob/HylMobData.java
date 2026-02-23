package com.lostale.hylostale.data.mob;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class HylMobData {

    public final Ref<EntityStore> ref;

    public int level;      // >= 1
    public int maxHp;      // >= 1
    public int hp;         // [0..maxHp]

    public String name;    // affichage
    public boolean hostile;

    // anti double reward + lifecycle
    public boolean rewarded;      // xp déjà donnée
    public boolean initialized;   // assign déjà fait

    public HylMobData(Ref<EntityStore> ref) {
        this.ref = ref;
        this.level = 1;
        this.maxHp = 1;
        this.hp = 1;
        this.name = "Mob";
        this.hostile = false;
        this.rewarded = false;
        this.initialized = false;
    }

    public void clamp() {
        if (level < 1) level = 1;
        if (maxHp < 1) maxHp = 1;
        if (hp < 0) hp = 0;
        if (hp > maxHp) hp = maxHp;

        if (name == null || name.isBlank()) name = "Mob";
    }

    public boolean isDead() {
        return hp <= 0;
    }
}
