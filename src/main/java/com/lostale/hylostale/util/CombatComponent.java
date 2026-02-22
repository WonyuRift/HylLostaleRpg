package com.lostale.hylostale.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class CombatComponent implements Component<EntityStore> {
    private static ComponentType<EntityStore, CombatComponent> COMPONENT_TYPE;

    public long lastCombatTime;

    public CombatComponent() {
    }

    public static ComponentType<EntityStore, CombatComponent> getComponentType() {
        if (COMPONENT_TYPE == null)
            throw new IllegalStateException("CombatComponent has not been registered!");
        return COMPONENT_TYPE;
    }

    public static void setComponentType(ComponentType<EntityStore, CombatComponent> type) {
        COMPONENT_TYPE = type;
    }

    public void markInCombat() {
        this.lastCombatTime = System.currentTimeMillis();
    }

    public void markNotInCombat() {
        this.lastCombatTime = 0L;
    }

    public boolean isInCombat() {
        return
                (System.currentTimeMillis() - this.lastCombatTime < 10 * 1000L);
    }

    public long getTimeRemaining() {
        return Math.max(0L, 10 * 1000L - System.currentTimeMillis() - this.lastCombatTime);
    }

    @Nonnull
    public Component<EntityStore> clone() {
        CombatComponent copy = new CombatComponent();
        copy.lastCombatTime = this.lastCombatTime;
        return copy;
    }
}
