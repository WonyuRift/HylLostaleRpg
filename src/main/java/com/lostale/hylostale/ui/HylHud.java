package com.lostale.hylostale.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class HylHud extends CustomUIHud {

    public HylHud(@NonNullDecl PlayerRef playerRef) {
        super(playerRef);
    }
    private volatile boolean mounted = false;
    private volatile String pendingTargetName = "";

    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("Hud/LostaleHud.ui"); // resources/Common/UI/Custom/ExpHud.ui
        mounted = true;
    }

    public void updateExperience(int current, int max, int level) {
        float ratio = (max <= 0) ? 0f : Math.max(0f, Math.min(1f, (float) current / (float) max));
        String pendingText = current + " / " + max;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#Exp.Value", ratio);
        ui.set("#ExpLabel.TextSpans", Message.raw(pendingText));
        ui.set("#LevelLabel.TextSpans", Message.raw(String.valueOf(level)));
        update(false, ui); // false = ne pas clear le reste
    }

    public void updateHealthBar(int current, int max) {
        float ratio = (max <= 0) ? 0f : Math.max(0f, Math.min(1f, (float) current / (float) max));
        String pendingText = current + " / " + max;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#Vie.Value", ratio);
        ui.set("#VieLabel.TextSpans", Message.raw(pendingText));
        update(false, ui);
    }

    public void setTargetName(String name) {
        pendingTargetName = (name == null) ? "" : name;
        if (!mounted) return;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#HudJoueur #TargetName.TextSpans", Message.raw(pendingTargetName));
        update(false, ui);
    }

    public void clearTargetName() {
        pendingTargetName = "";
        if (!mounted) return;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#HudJoueur #TargetName.TextSpans", Message.raw(pendingTargetName));
        update(false, ui);
    }
}
