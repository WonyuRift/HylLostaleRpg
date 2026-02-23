package com.lostale.hylostale.service.ui;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.service.player.HylPlayerExpService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.ui.HylHud;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HylHudService {

    private final HylPlayerManager mgr;
    private final HylPlayerStatsService stats;
    private final HylPlayerExpService xp;

    private final Map<UUID, HylHud> hudByPlayer = new ConcurrentHashMap<>();

    public HylHudService(@Nonnull HylPlayerManager mgr,
                      @Nonnull HylPlayerStatsService stats,
                      @Nonnull HylPlayerExpService xp) {
        this.mgr = mgr;
        this.stats = stats;
        this.xp = xp;
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent e) {
        Player p = e.getPlayer();
        PlayerRef ref = p.getPlayerRef();
        UUID id = p.getUuid();

        // charge data + recalc dérivés (maxHp/maxMana) si nécessaire
        mgr.get(id);
        stats.recompute(id);

        // HUD session
        HylHud hud = new HylHud(ref);
        hudByPlayer.put(id, hud);

        // Attache HUD au joueur
        p.getHudManager().setCustomHud(ref, hud);

        // ---- Désactivation HUD vanilla ----
        p.getHudManager().hideHudComponents(ref, HudComponent.Health, HudComponent.Stamina, HudComponent.Mana);

        // Rendu initial
        renderAll(id);
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent e) {
        UUID id = e.getPlayerRef().getUuid();
        removeHud(e.getPlayerRef());
        hudByPlayer.remove(id);
    }

    public void renderAll(@Nonnull UUID id) {
        HylPlayerData d = mgr.get(id);
        HylHud hud = hudByPlayer.get(id);
        if (hud == null) return;

        hud.updateHealthBar(d.hp, d.maxHp);
        hud.updateExperience(d.xp, xp.xpToNext(d.level), d.level);

        // Si ton UI a Mana + label, ajoute updateMana(...) dans HylHud
        // hud.updateMana(d.mana, d.maxMana);
    }

    public void renderHealth(@Nonnull UUID id) {
        HylPlayerData d = mgr.get(id);
        HylHud hud = hudByPlayer.get(id);
        if (hud == null) return;
        hud.updateHealthBar(d.hp, d.maxHp);
    }

    public void renderXp(@Nonnull UUID id) {
        HylPlayerData d = mgr.get(id);
        HylHud hud = hudByPlayer.get(id);
        if (hud == null) return;
        hud.updateExperience(d.xp, xp.xpToNext(d.level), d.level);
    }

    public void renderMana(@Nonnull UUID id) {
        HylPlayerData d = mgr.get(id);
        HylHud hud = hudByPlayer.get(id);
        if (hud == null) return;

        // nécessite une méthode HylHud.updateMana(...)
        // hud.updateMana(d.mana, d.maxMana);
    }

    public void removeHud(@NonNullDecl PlayerRef playerRef) {
        if (playerRef == null) return;
        hudByPlayer.remove(playerRef.getUuid());
    }

    public HylHud getHud(@Nonnull UUID id) {
        return hudByPlayer.get(id);
    }
}
