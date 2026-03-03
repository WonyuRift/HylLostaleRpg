package com.lostale.hylostale.ui.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.service.region.HylRegionEditorService;

import javax.annotation.Nonnull;
import java.util.UUID;

public class HylRegionEditorPage extends InteractiveCustomUIPage<HylRegionEditorPage.EditorEventData> {

    private static final int PAGE_SIZE = 10;
    private final HylRegionEditorService editor;

    public HylRegionEditorPage(@Nonnull PlayerRef playerRef, @Nonnull HylRegionEditorService editor) {
        super(playerRef, CustomPageLifetime.CanDismiss, EditorEventData.CODEC);
        this.editor = editor;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
                      UIEventBuilder events, Store<EntityStore> store) {
        commands.append("Pages/LostaleRegionEdit.ui");

        // Bind save button (note: keys that start with letters must be uppercase)
        bindStatic(events);

        render(commands, events);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                EditorEventData data) {
        UUID pid = playerRef.getUuid();
        if (data == null || data.action == null) return;

        switch (data.action) {
            case "prev" -> editor.pagePrev(pid);

            case "next" -> {
                int total = totalPages(pid);
                editor.pageNext(pid, total);
            }

            case "select" -> {
                if (data.regionId != null) editor.selectRegion(pid, data.regionId);
            }

            case "min_dec" -> editor.bumpMin(playerRef.getUuid(), -1);
            case "min_inc" -> editor.bumpMin(playerRef.getUuid(), +1);
            case "max_dec" -> editor.bumpMax(playerRef.getUuid(), -1);
            case "max_inc" -> editor.bumpMax(playerRef.getUuid(), +1);
            case "reload"  -> editor.reload(playerRef.getUuid());
            case "save"    -> editor.save(playerRef.getUuid());
            case "close" -> { editor.close(playerRef.getUuid()); close(); return; }
            default -> { return; }
        }

        UICommandBuilder u = new UICommandBuilder();
        UIEventBuilder e = new UIEventBuilder();
        bindStatic(e);
        render(u, e);
        sendUpdate(u, new UIEventBuilder(), false);
    }

    private void render(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events) {
        UUID pid = playerRef.getUuid();

        var v = editor.view(pid);
        editor.syncOrbisRegionsIntoConfig(v.worldKey());
        ui.set("#RegionLine.Text", "World: " + v.worldKey() + " | Region: " + v.regionId());
        ui.set("#MinValue.Text", String.valueOf(v.min()));
        ui.set("#MaxValue.Text", String.valueOf(v.max()));
        ui.set("#Status.Text", v.status() == null ? "" : v.status());

        // liste paginée
        java.util.List<String> regions = editor.listRegions(pid);
        int page = editor.getPage(pid);
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            String selector = "#RegionRow" + i;
            int idx = start + i;

            if (idx >= 0 && idx < regions.size()) {
                String id = regions.get(idx);

                ui.set(selector + ".Text", id);

                // rebind avec regionId
                bind(events, selector, "select", id);
            } else {
                ui.set(selector + ".Text", "-");
                // bind “select” vide (ou pas de bind)
                bind(events, selector, "select", "");
            }
        }

        int totalPages = totalPages(pid);
        ui.set("#RegionsTitle.Text", "Regions (" + (regions.isEmpty() ? 0 : (page + 1)) + "/" + Math.max(1, totalPages) + ")");
    }

    private int totalPages(UUID pid) {
        int n = editor.listRegions(pid).size();
        return Math.max(1, (n + PAGE_SIZE - 1) / PAGE_SIZE);
    }


    private void bindStatic(@Nonnull UIEventBuilder events) {

        bind(events, "#PrevPage", "prev", null);
        bind(events, "#NextPage", "next", null);

        bind(events, "#MinDec", "min_dec", null);
        bind(events, "#MinInc", "min_inc", null);
        bind(events, "#MaxDec", "max_dec", null);
        bind(events, "#MaxInc", "max_inc", null);
        bind(events, "#Reload", "reload", null);
        bind(events, "#Save",   "save", null);
        bind(events, "#Close",  "close", null);

    }

    private static void bind(@Nonnull UIEventBuilder events, @Nonnull String selector, @Nonnull String action, String regionId) {
        EventData d = new EventData().append("Action", action);
        if (regionId != null) d.append("RegionId", regionId);
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, d);
    }

    public static class EditorEventData {
        public static final BuilderCodec<EditorEventData> CODEC =
                BuilderCodec.builder(EditorEventData.class, EditorEventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                        .add()
                        .append(new KeyedCodec<>("RegionId", Codec.STRING), (d, v) -> d.regionId = v, d -> d.regionId)
                        .add().build();

        public String action;
        public String regionId;
    }
}
