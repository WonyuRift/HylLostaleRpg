package com.lostale.hylostale.entity.player.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.UUID;

public class HylDamagePlayerFilter extends DamageEventSystem {

    private final HylPlayerStatsService stats;
    private final HylHudService huds;

    public HylDamagePlayerFilter(@Nonnull HylPlayerStatsService stats, @Nonnull HylHudService huds) {
        this.stats = stats;
        this.huds = huds;
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return Query.any();
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
        if (targetRef == null || !targetRef.isValid()) return;

        Player target = store.getComponent(targetRef, Player.getComponentType());
        if (target == null) return; // on gère uniquement les joueurs ici

        // Annule le système de dégâts vanilla
        damage.setCancelled(true);

        // Applique les dégâts RPG
        int amount = (int) Math.ceil(Math.max(0.0f, damage.getAmount()));
        if (amount <= 0) return;

        UUID id = target.getUuid();

        // Combat flag (regen off etc.)
        stats.markCombat(id);

        // Damage RPG + HUD
        var d = stats.damage(id, amount);
        huds.renderHealth(id);

        // Mort RPG minimale: reset full HP (tu remplaceras par respawn/teleport)
        if (d != null && d.hp <= 0) {
            stats.healToFull(id);
            huds.renderHealth(id);
        }
    }

    // Optionnel: utilitaire pour clear/unregister, pas utilisé ici
    public void onPlayerDisconnect(@NonNullDecl UUID uuid) {
        // rien à faire: data gérée par PlayerRpgManager.unload()
    }
}
