package com.lostale.hylostale.ui;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.lostale.hylostale.services.hud.HylHudService;
import com.lostale.hylostale.util.system.UiQueue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HylHudManager {

    private final HylHudService stats;

    private final Map<UUID, HylHud> hudMap = new ConcurrentHashMap<>();

    public HylHudManager(HylHudService stats) {
        this.stats = stats;
    }

    public void onPlayerReady(PlayerReadyEvent e) {
        Player p = e.getPlayer();
        PlayerRef ref = p.getPlayerRef();
        UUID id = p.getUuid();

        hideVanillaElements(e);

        HylHud lostaleHud = new HylHud(ref);
        hudMap.put(id, lostaleHud);
        p.getHudManager().setCustomHud(ref, lostaleHud);

        renderAll(id);
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent e) {
        hudMap.remove(e.getPlayerRef().getUuid());
    }

    public void renderAll(UUID id) {
        HylHudService.PlayerData st = stats.ensure(id);
        renderHealth(id, st);
        renderXp(id, st);
    }

    public void renderHealth(UUID id) {
        renderHealth(id, stats.ensure(id));
    }

    private void renderHealth(UUID id, HylHudService.PlayerData st) {
        HylHud hud = hudMap.get(id);
        if (hud != null)
            UiQueue.post(() -> hud.updateHealthBar(st.hp(), st.maxHp()));
    }

    public void renderXp(UUID id) {
        renderXp(id, stats.ensure(id));
    }

    private void renderXp(UUID id, HylHudService.PlayerData st) {
        HylHud hud = hudMap.get(id);
        if (hud != null)
            UiQueue.post(() -> hud.updateExperience(st.xp(), stats.nextThreshold(st.level()), st.level()));
    }

    private void hideVanillaElements(PlayerReadyEvent e) {
        Player p = e.getPlayer();
        PlayerRef ref = p.getPlayerRef();

        // Désactiver HUD vanilla
        p.getHudManager().hideHudComponents(ref, HudComponent.Health, HudComponent.Stamina);
    }

    public HylHud getHud(UUID playerId) {
        return hudMap.get(playerId);
    }

    public java.util.Set<UUID> getOnlineIds() {
        return java.util.Collections.unmodifiableSet(hudMap.keySet());
    }
}
