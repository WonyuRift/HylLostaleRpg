package com.lostale.hylostale.data;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class HylData implements AutoCloseable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dataDir;
    private Connection cx;

    public HylData(Path pluginDataDir) {
        this.dataDir = pluginDataDir;
    }

    public void init() {
        if (cx != null) return;

        try {
            Files.createDirectories(dataDir);

            // Force load SQLite driver (évite "No suitable driver" en environnement plugin)
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("SQLite driver missing: add org.xerial:sqlite-jdbc (shaded in jar)", e);
            }

            Path dbFile = dataDir.resolve("lostale.db");
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            cx = DriverManager.getConnection(url);

            try (var st = cx.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
            }

            LOGGER.atInfo().log("DB ready: %s", dbFile.toAbsolutePath());

        } catch (IOException e) {
            throw new RuntimeException("Failed to init DB: cannot create plugin data dir: " + dataDir, e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init DB: JDBC error", e);
        }
    }

    public Connection connection() {
        if (cx == null) throw new IllegalStateException("DB not initialized (call init() first)");
        return cx;
    }

    @Override
    public void close() {
        if (cx == null) return;
        try {
            cx.close();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close DB", e);
        } finally {
            cx = null;
        }
    }
}
