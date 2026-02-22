package com.lostale.hylostale.ecs.stamina;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.stamina.StaminaSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.util.CombatComponent;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

public class StaminaVanillaCancelSystem extends EntityTickingSystem<EntityStore> {

    private final Map<String, Float> staminaMap;
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies;

    public StaminaVanillaCancelSystem(Map<String, Float> staminaMap) {
        this.query = Query.and(Player.getComponentType(), MovementStatesComponent.getComponentType(), EntityStatMap.getComponentType());
        this.dependencies = Set.of(new SystemDependency<>(Order.AFTER, StaminaSystems.SprintStaminaEffectSystem.class));
        this.staminaMap = staminaMap;
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return this.dependencies;
    }

    @Override
    public void tick(float v, int index, @NonNullDecl ArchetypeChunk<EntityStore> chunk, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> commandBuffer) {
        MovementStatesComponent statesComponent = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        if (statesComponent == null) return;
        MovementStates states = statesComponent.getMovementStates();
        if (states == null) return;
        if (!states.sprinting) return;
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        float currentStaminaValue = getStamina(index, chunk);
        if (this.staminaMap.containsKey(player.getDisplayName())) {
            float previousStaminaValue = this.staminaMap.get(player.getDisplayName());
            float drop = previousStaminaValue - currentStaminaValue;
            if (drop > 0.0F && drop < 1.0F) {
                setStamina(index, chunk, previousStaminaValue);
            } else {
                this.staminaMap.put(player.getDisplayName(), currentStaminaValue);
            }
        } else {
            this.staminaMap.put(player.getDisplayName(), currentStaminaValue);
        }
    }

    private float getStamina(int index, ArchetypeChunk<EntityStore> archetypeChunk) {
        EntityStatMap statMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType());
        if (statMap != null) {
            int staminaIndex = DefaultEntityStatTypes.getStamina();
            EntityStatValue staminaValue = statMap.get(staminaIndex);
            return (staminaValue != null) ? staminaValue.get() : 0.0F;
        }
        return 0.0F;
    }

    private void setStamina(int index, ArchetypeChunk<EntityStore> archetypeChunk, float amount) {
        EntityStatMap statMap = archetypeChunk.getComponent(index, EntityStatMap.getComponentType());
        if (statMap != null) {
            int staminaIndex = DefaultEntityStatTypes.getStamina();
            statMap.setStatValue(staminaIndex, amount);
        }
    }
}
