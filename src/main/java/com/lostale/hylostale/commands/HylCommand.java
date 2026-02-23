package com.lostale.hylostale.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.lostale.hylostale.ui.HylHudManager;
import com.lostale.hylostale.services.hud.HylHudService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HylCommand extends AbstractAsyncCommand {

    private final RequiredArg<String> category;
    private final RequiredArg<String> action;
    private final RequiredArg<String> playerName;
    private final RequiredArg<Integer> value;

    private final HylHudService stats;
    private final HylHudManager huds;

    public HylCommand(HylHudService stats, HylHudManager huds) {
        super("rpg", "RPG debug");
        this.stats = stats;
        this.huds = huds;

        this.category = withRequiredArg("category", "xp|hp", ArgTypes.STRING);
        this.action = withRequiredArg("action", "add|set|heal|damage", ArgTypes.STRING);
        this.playerName = withRequiredArg("player", "Nom", ArgTypes.STRING);
        this.value = withRequiredArg("value", "Valeur", ArgTypes.INTEGER);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx) {
        String c = category.get(ctx).toLowerCase();
        String a = action.get(ctx).toLowerCase();
        String name = playerName.get(ctx);
        int v = value.get(ctx);

        Universe u = Universe.get(); // adapte si nécessaire
        PlayerRef p = u.getPlayerByUsername(name, NameMatching.EXACT);
        if (p == null) {
            ctx.sendMessage(Message.raw("Joueur introuvable: " + name));
            return CompletableFuture.completedFuture(null);
        }

        UUID id = p.getUuid();
        HylHudService.PlayerData st;

        if ("xp".equals(c) && "add".equals(a)) {
            st = stats.addXp(id, v);
            huds.renderAll(id);
            ctx.sendMessage(Message.raw("OK " + name + " -> " + stats.formatXp(st)));
            return CompletableFuture.completedFuture(null);
        }

        if ("hp".equals(c)) {
            if ("set".equals(a)) st = stats.setHp(id, v);
            else if ("heal".equals(a)) st = stats.heal(id, v);
            else if ("damage".equals(a)) st = stats.damage(id, v);
            else {
                ctx.sendMessage(Message.raw("Usage: /rpg hp set|heal|damage <player> <value>"));
                return CompletableFuture.completedFuture(null);
            }
            huds.renderHealth(id);
            ctx.sendMessage(Message.raw("OK " + name + " -> HP " + st.hp() + "/" + st.maxHp()));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(Message.raw("Usage: /rpg xp add <player> <value> | /rpg hp set|heal|damage <player> <value>"));
        return CompletableFuture.completedFuture(null);
    }
}
