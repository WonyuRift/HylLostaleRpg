package com.lostale.hylostale.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

public final class HylHud extends CustomUIHud {

    public HylHud(@NonNullDecl PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("Hud/LostaleHud.ui"); // resources/Common/UI/Custom/ExpHud.ui
    }

    public void updateText(@Nonnull String newText) {
        UICommandBuilder ui = new UICommandBuilder();
        //ui.set("#LostaleLevelLabel.TextSpans", Message.raw(newText));
        update(false, ui); // false = ne pas clear le reste
    }

    public void updateHealthBar(int current, int max) {
        float ratio = (max <= 0) ? 0f : Math.max(0f, Math.min(1f, (float) current / (float) max));
        String pendingText = current + " / " + max;

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#Vie.Value", ratio);
        //ui.set("#LostaleHpLabel.TextSpans", Message.raw(pendingText));
        update(false, ui);
    }
}
