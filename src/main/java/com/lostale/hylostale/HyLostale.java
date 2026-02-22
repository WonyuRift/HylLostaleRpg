package com.lostale.hylostale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.lostale.hylostale.commands.HylCommand;
import com.lostale.hylostale.ecs.damage.DamageFilterSystem;
import com.lostale.hylostale.ecs.stamina.StaminaVanillaCancelSystem;
import com.lostale.hylostale.store.HylData;
import com.lostale.hylostale.ui.HylHudManager;
import com.lostale.hylostale.ui.HylHudService;
import com.lostale.hylostale.util.system.UiDrainTickSystem;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

public final class HyLostale extends JavaPlugin {

    private HylData store;
    private HylHudService stats;
    private HylHudManager ready;
    private Timer timer;
    public static HytaleLogger LOG;
    private Map<String, Float> staminaMap;

    public HyLostale(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        store = new HylData(getDataDirectory().toAbsolutePath());
        store.init();
        LOG = getLogger();
        staminaMap = new HashMap<>();

        stats = new HylHudService(store);
        ready = new HylHudManager(stats);

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, ready::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, ready::onPlayerDisconnect);
        getCommandRegistry().registerCommand(new HylCommand(stats, ready));

        getEntityStoreRegistry().registerSystem(new StaminaVanillaCancelSystem(staminaMap));
        getEntityStoreRegistry().registerSystem(new DamageFilterSystem(stats, ready));
        getEntityStoreRegistry().registerSystem(new UiDrainTickSystem());

        timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() {
                if (stats != null) stats.flushDirty();
            }
        }, 3000L, 3000L);

        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
                    PlayerRef player = event.getPlayerRef();
                    this.staminaMap.remove(player.getUuid().toString());
                });

    }


    @Override
    protected void shutdown() {
        if (timer != null) timer.cancel();
        if (stats != null) stats.flushAll(); // dernier flush forcé
    }


    private Path resolveWorldDir() {
        return getDataDirectory().toAbsolutePath();
    }
}
