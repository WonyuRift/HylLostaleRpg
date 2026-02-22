package com.lostale.hylostale.util.system;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class UiDrainTickSystem extends TickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int systemIndex, Store<EntityStore> store) {
        UiQueue.drain(200); // limite par tick
    }
}
