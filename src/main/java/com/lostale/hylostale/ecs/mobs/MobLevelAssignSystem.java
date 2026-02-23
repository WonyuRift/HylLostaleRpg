package com.lostale.hylostale.ecs.mobs;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.lostale.hylostale.services.mobs.MobInfoService;
import com.lostale.hylostale.services.mobs.MobLevelService;
import com.lostale.hylostale.util.system.MobHpScaler;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.concurrent.ThreadLocalRandom;

public class MobLevelAssignSystem extends EntityTickingSystem {

    private final MobLevelService mobLevels;
    private final Query query = Query.not(Player.getComponentType()); // adapte si Query diffère
    private final MobInfoService mobInfo;

    private static final int MIN_LVL = 1;
    private static final int MAX_LVL = 99;

    @Override
    public Query getQuery() { return query; }

    @Override
    public boolean isParallel(int archetype, int archetypeChunkIndex) {
        return false;
    }

    public MobLevelAssignSystem(MobLevelService mobLevels, MobInfoService mobInfo) {
        this.mobLevels = mobLevels;
        this.mobInfo = mobInfo;
    }

    @Override
    public void tick(float dt, int index, @NonNullDecl ArchetypeChunk chunk, @NonNullDecl Store store, @NonNullDecl CommandBuffer commandBuffer) {

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null) return;

        Player p = (Player) store.getComponent(ref, Player.getComponentType());
        if (p != null) return;

        Entity baseEntity = EntityUtils.getEntity(index, chunk);
        if (!(baseEntity instanceof NPCEntity livingEntity)) return;

        Role role = livingEntity.getRole();
        if (role == null) return;

        WorldSupport ws = role.getWorldSupport();
        if (ws == null) return;

        if (mobLevels.has(ref)) return;
        int lvl = ThreadLocalRandom.current().nextInt(MIN_LVL, MAX_LVL + 1);

        int baseMaxHp = 50;
        double mult = 1.0 + 0.08 * (lvl - 1);
        int maxHp = (int) Math.round(baseMaxHp * mult);

        mobLevels.set(ref, new MobLevelService.MobState(lvl, maxHp, maxHp));

        MobHpScaler.apply(store, commandBuffer, ref, lvl);

        String mobName = null;
        try { mobName = String.valueOf(role.getRoleName()); } catch (Throwable ignored) {}
        if (mobName == null || mobName.isBlank()) {
            try { mobName = String.valueOf(role); } catch (Throwable ignored) {}
        }

        if (mobName == null || mobName.isBlank()) mobName = "Hostile";
        mobName = formatMobName(mobName);

        mobInfo.setName(ref, mobName);
    }

    private static String formatMobName(String raw) {
        if (raw == null) return "Mob";
        String s = raw.trim();
        if (s.isEmpty()) return "Mob";

        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < s.length()) s = s.substring(slash + 1);

        s = s.replaceAll("([a-z])([A-Z])", "$1 $2");
        s = s.replace('_', ' ').replace('-', ' ');
        s = s.replaceAll("\\s+", " ").trim();

        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1).toLowerCase());
            out.append(' ');
        }
        s = out.toString().trim();

        return s.isEmpty() ? "Mob" : s;
    }
}
