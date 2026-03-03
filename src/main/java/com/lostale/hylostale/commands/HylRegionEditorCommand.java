package com.lostale.hylostale.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.service.region.HylRegionEditorService;
import com.lostale.hylostale.ui.pages.HylRegionEditorPage;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.UUID;

public class HylRegionEditorCommand extends AbstractPlayerCommand {

    private final HylRegionEditorService editor;

    public HylRegionEditorCommand(HylRegionEditorService editor) {
        super("regionedit", "Edit mob level range for current region", false);
        this.editor = editor;
    }

    @Override
    protected void execute(@NonNullDecl CommandContext ctx,
                           @NonNullDecl Store<EntityStore> store,
                           @NonNullDecl Ref<EntityStore> ref,
                           @NonNullDecl PlayerRef playerRef,
                           @NonNullDecl World world) {

        Player p = store.getComponent(ref, Player.getComponentType());
        if (p == null) return;
        editor.openFor(p);
        p.getPageManager().openCustomPage(ref, store, new HylRegionEditorPage(playerRef, editor));
    }
}
