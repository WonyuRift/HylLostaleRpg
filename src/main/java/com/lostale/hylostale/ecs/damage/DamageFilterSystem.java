package com.lostale.hylostale.ecs.damage;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.ecs.health.HealthRegenManager;
import com.lostale.hylostale.services.hud.HylHudService;
import com.lostale.hylostale.ui.HylHudManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class DamageFilterSystem extends DamageEventSystem {

    private final HylHudService stats;
    private final HylHudManager huds;
    private final HealthRegenManager regen;
    private final Query<EntityStore> query;

    public DamageFilterSystem(HylHudService stats, HylHudManager huds, HealthRegenManager regen) {
        this.stats = stats;
        this.huds = huds;
        this.regen = regen;
        this.query = Query.and(Player.getComponentType(), DamageDataComponent.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public @Nonnull SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        Player target = store.getComponent(targetRef, Player.getComponentType());
        if (target == null) return;

        damage.setCancelled(true);
        int amount = (int) Math.ceil(damage.getAmount());
        PlayerRef ref = target.getPlayerRef();
        UUID id = target.getUuid();
        regen.markInCombat(id);

        HylHudService.PlayerData st = stats.damage(id, amount);

        huds.renderHealth(id);

        if (stats.isDead(st)) {
            // mort RPG minimal: remet full hp + update HUD (à remplacer par respawn/teleport custom)
            stats.setHp(id, st.maxHp());
            huds.renderHealth(id);
        }
    }
}
