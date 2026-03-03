package com.lostale.hylostale;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.lostale.hylostale.commands.HylRegionEditorCommand;
import com.lostale.hylostale.commands.HylCommand;
import com.lostale.hylostale.config.HylConfig;
import com.lostale.hylostale.config.HylRegionLevelConfig;
import com.lostale.hylostale.data.HylData;
import com.lostale.hylostale.data.player.HylPlayerDB;
import com.lostale.hylostale.data.repo.player.HylPlayerRepository;
import com.lostale.hylostale.entity.mob.HylMobManager;
import com.lostale.hylostale.entity.mob.systems.HylMobDamageFilterSystem;
import com.lostale.hylostale.entity.mob.systems.HylMobLevelAssignSystem;
import com.lostale.hylostale.entity.mob.systems.HylMobTargetInfoSystem;
import com.lostale.hylostale.entity.player.HylPlayerManager;
import com.lostale.hylostale.entity.player.systems.HylDamagePlayerFilter;
import com.lostale.hylostale.entity.player.systems.HylRegenManager;
import com.lostale.hylostale.service.mob.HylMobStatsService;
import com.lostale.hylostale.service.mob.HylMobXpService;
import com.lostale.hylostale.service.mob.HylZoneLevelService;
import com.lostale.hylostale.service.player.HylPlayerExpService;
import com.lostale.hylostale.service.player.HylPlayerStatsService;
import com.lostale.hylostale.service.region.HylOrbisGuardRegionService;
import com.lostale.hylostale.service.region.HylRegionEditorService;
import com.lostale.hylostale.service.ui.HylHudService;
import com.lostale.hylostale.utils.vanilla.cancel.HylStaminaVanillaCancelSystem;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class HyLostale extends JavaPlugin {

    // DB
    private HylData db;
    private HylPlayerDB playerDb;
    private HylPlayerRepository playerRepo;
    private HylZoneLevelService mobLevelService;

    // Config
    private HylConfig cfg;
    private Path regionCfgFile;
    private HylRegionLevelConfig regionCfg;
    private HylOrbisGuardRegionService regionResolver;
    private HylRegionEditorService editor;

    // Player RPG core
    private HylPlayerManager players;
    private HylPlayerStatsService stats;
    private HylPlayerExpService xp;

    // HUD
    private HylHudService huds;

    // Regen
    private HylRegenManager regen;

    // Mob RPG core
    private HylMobManager mobs;
    private HylMobStatsService mobStats;
    private HylMobXpService mobXp;

    //Vanilla
    private Map<String, Float> vanillaStaminaMap;

    public HyLostale(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {

        // --------------------
        // 1) DB
        // --------------------
        db = new HylData(getDataDirectory().toAbsolutePath());
        db.init();

        playerDb = new HylPlayerDB(db.connection());
        playerDb.initSchema();
        playerRepo = playerDb;
        mobLevelService = new HylZoneLevelService(/*worldSeed*/ 12345L, /*cell*/ 64, /*min*/ 1, /*max*/ 50);

        // --------------------
        // 2) Config
        // --------------------
        cfg = new HylConfig(); // plus tard: loader json
        regionCfgFile = getDataDirectory().resolve("region_levels.json");
        regionCfg = HylRegionLevelConfig.loadOrCreate(regionCfgFile);
        regionResolver = new HylOrbisGuardRegionService();
        editor = new HylRegionEditorService(regionCfgFile, regionCfg, regionResolver);

        // --------------------
        // 3) Player RPG stack
        // --------------------
        players = new HylPlayerManager(playerRepo);
        stats = new HylPlayerStatsService(players, cfg);
        xp = new HylPlayerExpService(players, cfg);

        // --------------------
        // 4) HUD et PAGES
        // --------------------
        huds = new HylHudService(players, stats, xp);

        // --------------------
        // 5) Regen hors combat
        // --------------------
        regen = new HylRegenManager(players, stats, huds, cfg);
        regen.start();

        // --------------------
        // 6) Mob RPG stack
        // --------------------
        mobs = new HylMobManager();
        mobStats = new HylMobStatsService(mobs, cfg);
        mobXp = new HylMobXpService(cfg);

        // --------------------
        // 7.1) Vanilla
        // --------------------
        vanillaStaminaMap = new HashMap<>();

        // --------------------
        // 7.2) Events
        // --------------------
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, (PlayerReadyEvent e) -> {
            huds.onPlayerReady(e);
            regen.onPlayerReady(e.getPlayer().getUuid());
        });

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, (PlayerDisconnectEvent e) -> {
            huds.onPlayerDisconnect(e);
            regen.onPlayerDisconnect(e.getPlayerRef().getUuid());
            players.unload(e.getPlayerRef().getUuid());
        });

        // --------------------
        // 8) Commands
        // --------------------
        getCommandRegistry().registerCommand(new HylCommand(players, stats, xp, huds));
        getCommandRegistry().registerCommand(new HylRegionEditorCommand(editor));

        // --------------------
        // 9) Systems
        // --------------------
        // Joueur: HP RPG
        getEntityStoreRegistry().registerSystem(new HylDamagePlayerFilter(stats, huds, mobs, mobStats, cfg, players));



        // Mob: assign level/hp
        getEntityStoreRegistry().registerSystem(new HylMobLevelAssignSystem(mobs, mobStats, regionCfg, regionResolver));



        // Mob: HP RPG + reward XP au kill
        getEntityStoreRegistry().registerSystem(new HylMobDamageFilterSystem(
                mobs, mobStats, mobXp,
                players, xp, stats, huds,
        cfg));

        // Vanilla: HP RPG
        getEntityStoreRegistry().registerSystem(new HylStaminaVanillaCancelSystem(vanillaStaminaMap));

        // Cible: afficher nom/level/hp dans HUD
        getEntityStoreRegistry().registerSystem(new HylMobTargetInfoSystem(huds, mobs));
    }

    @Override
    protected void shutdown() {
        if (regen != null) regen.close();
        if (players != null) players.saveAll();
        if (db != null) db.close();
    }
}
