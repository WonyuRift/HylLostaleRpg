package com.lostale.hylostale.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.lostale.hylostale.data.player.HylPlayerData;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.service.player.HylPlayerExpService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.ui.HylHudService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HylCommand extends AbstractAsyncCommand {

    private final RequiredArg<String> category;
    private final RequiredArg<String> action;
    private final RequiredArg<String> playerName;
    private final RequiredArg<Integer> value;

    private final HylPlayerManager players;
    private final HylPlayerStatsService stats;
    private final HylPlayerExpService xp;
    private final HylHudService huds;

    public HylCommand(HylPlayerManager players,
                      HylPlayerStatsService stats,
                      HylPlayerExpService xp,
                      HylHudService huds) {

        super("rpg", "RPG administration");

        this.players = players;
        this.stats = stats;
        this.xp = xp;
        this.huds = huds;

        this.category = withRequiredArg("category", "xp|hp", ArgTypes.STRING);
        this.action = withRequiredArg("action", "add|set|heal|damage", ArgTypes.STRING);
        this.playerName = withRequiredArg("player", "Nom du joueur", ArgTypes.STRING);
        this.value = withRequiredArg("value", "Valeur", ArgTypes.INTEGER);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {

        String c = category.get(ctx).toLowerCase();
        String a = action.get(ctx).toLowerCase();
        String name = playerName.get(ctx);
        int v = value.get(ctx);

        Universe u = Universe.get();
        PlayerRef pref = u.getPlayerByUsername(name, NameMatching.EXACT);

        if (pref == null) {
            ctx.sendMessage(Message.raw("Joueur introuvable: " + name));
            return CompletableFuture.completedFuture(null);
        }

        UUID id = pref.getUuid();
        HylPlayerData data = players.get(id);

        // --------------------
        // XP
        // --------------------
        if ("xp".equals(c)) {

            if (!"add".equals(a)) {
                ctx.sendMessage(Message.raw("Usage: /rpg xp add <player> <value>"));
                return CompletableFuture.completedFuture(null);
            }

            int before = data.level;
            xp.addXp(id, v);
            int after = players.get(id).level;

            if (after > before) {
                stats.recompute(id, true);
            }

            huds.renderXp(id);
            huds.renderHealth(id);

            ctx.sendMessage(Message.raw(
                    "XP OK -> Lvl " + data.level + " | XP " + data.xp
            ));

            return CompletableFuture.completedFuture(null);
        }

        // --------------------
        // HP
        // --------------------
        if ("hp".equals(c)) {

            switch (a) {

                case "set" -> stats.setHp(id, v);
                case "heal" -> stats.heal(id, v);
                case "damage" -> stats.damage(id, v);
                default -> {
                    ctx.sendMessage(Message.raw(
                            "Usage: /rpg hp set|heal|damage <player> <value>"
                    ));
                    return CompletableFuture.completedFuture(null);
                }
            }

            huds.renderHealth(id);

            HylPlayerData d = players.get(id);

            ctx.sendMessage(Message.raw(
                    "HP OK -> " + d.hp + "/" + d.maxHp
            ));

            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(Message.raw(
                "Usage: /rpg xp add <player> <value> | /rpg hp set|heal|damage <player> <value>"
        ));

        return CompletableFuture.completedFuture(null);
    }
}
