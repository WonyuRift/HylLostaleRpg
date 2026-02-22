package com.lostale.hylostale.store;

import com.lostale.hylostale.ui.HylHudService;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public final class HylData {



    private final String jdbcUrl;

    public HylData(Path dataDir) {
        Path db = dataDir.resolve("lostale_data").resolve("players.db");
        this.jdbcUrl = "jdbc:sqlite:" + db.toAbsolutePath();
    }

    public void init() {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException ex) { throw new RuntimeException("SQLite JDBC missing", ex); }
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_rpg (
                  uuid TEXT PRIMARY KEY,
                  level INTEGER NOT NULL,
                  xp INTEGER NOT NULL,
                  max_hp INTEGER NOT NULL,
                  hp INTEGER NOT NULL,
                  max_sta INTEGER NOT NULL,
                  sta INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                );
            """);
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    public HylHudService.PlayerData load(UUID uuid) {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("""
                 SELECT level,xp,max_hp,hp,max_sta,sta
                 FROM player_rpg WHERE uuid = ?
             """)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                int level = rs.getInt(1);
                int xp = rs.getInt(2);
                int maxHp = rs.getInt(3);
                int hp = rs.getInt(4);

                return new HylHudService.PlayerData(level, xp, maxHp, hp);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB load failed: " + uuid, e);
        }
    }

    public void upsert(UUID uuid, HylHudService.PlayerData st) {
        long now = Instant.now().toEpochMilli();

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO player_rpg(uuid,level,xp,max_hp,hp,max_sta,sta,updated_at)
                 VALUES(?,?,?,?,?,?,?,?)
                 ON CONFLICT(uuid) DO UPDATE SET
                   level=excluded.level,
                   xp=excluded.xp,
                   max_hp=excluded.max_hp,
                   hp=excluded.hp,
                   max_sta=excluded.max_sta,
                   sta=excluded.sta,
                   updated_at=excluded.updated_at
             """)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, st.level());
            ps.setInt(3, st.xp());
            ps.setInt(4, st.maxHp());
            ps.setInt(5, st.hp());
            ps.setLong(6, now);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB upsert failed: " + uuid, e);
        }
    }


    //OLDDATA
    /*private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, LostaleHudService.RpgState>>(){}.getType();
    private final Object ioLock = new Object();
    private volatile  boolean dirty = false;

    private final Path file;
    private final ConcurrentHashMap<UUID, LostaleHudService.RpgState> data = new ConcurrentHashMap<>();

    public PersistantRpgStore(Path worldDir) {
        this.file = worldDir.resolve("lostale_data").resolve("players.json");
    }

    public void load() {
        data.clear();
        if (!Files.exists(file)) return;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, LostaleHudService.RpgState> raw = GSON.fromJson(r, MAP_TYPE);
            if (raw == null) return;
            raw.forEach((k, v) -> {
                try { data.put(UUID.fromString(k), v); } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load XP store: " + file, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, LostaleHudService.RpgState> raw = new java.util.HashMap<>();
            data.forEach((k, v) -> raw.put(k.toString(), v));

            try (BufferedWriter w = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(raw, MAP_TYPE, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save XP store: " + file, e);
        }
    }

    public LostaleHudService.RpgState get(UUID uuid) {
        return data.get(uuid);
    }

    public void put(UUID uuid, LostaleHudService.RpgState state) {
        data.put(uuid, state);
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void flushIfDirty() {
        if (!dirty) return;
        synchronized (ioLock) {
            if (!dirty) return;
            saveUnsafeLocked();
            dirty = false;
        }
    }

    private void saveUnsafeLocked() {
        try {
            Files.createDirectories(file.getParent());

            Map<String, LostaleHudService.RpgState> raw = new java.util.HashMap<>();
            data.forEach((k, v) -> raw.put(k.toString(), v));

            // écriture atomique: tmp -> move
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");

            try (BufferedWriter w = Files.newBufferedWriter(
                    tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                GSON.toJson(raw, MAP_TYPE, w);
            }

            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save RPG store: " + file, e);
        }
    }*/
}
