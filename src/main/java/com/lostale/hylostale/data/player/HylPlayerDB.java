package com.lostale.hylostale.data.player;

import com.lostale.hylostale.data.repo.player.HylPlayerRepository;

import java.sql.*;
import java.util.UUID;

public class HylPlayerDB implements HylPlayerRepository {

    private final Connection cx;

    public HylPlayerDB(Connection cx) {
        this.cx = cx;
    }

    public void initSchema() {
        try (Statement st = cx.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_rpg (
                  uuid            TEXT PRIMARY KEY,
                  level           INTEGER NOT NULL,
                  xp              INTEGER NOT NULL,
                  max_hp          INTEGER NOT NULL,
                  hp              INTEGER NOT NULL,
                  max_mana        INTEGER NOT NULL,
                  mana            INTEGER NOT NULL,
                  combat_until_ms INTEGER NOT NULL
                )
            """);

            // migration douce (ignore si colonne existe déjà)
            tryAddColumn(st, "player_rpg", "max_mana INTEGER NOT NULL DEFAULT 0");
            tryAddColumn(st, "player_rpg", "mana INTEGER NOT NULL DEFAULT 0");
            tryAddColumn(st, "player_rpg", "combat_until_ms INTEGER NOT NULL DEFAULT 0");
            tryAddColumn(st, "player_rpg", "str INTEGER NOT NULL DEFAULT 1");
            tryAddColumn(st, "player_rpg", "dex INTEGER NOT NULL DEFAULT 1");
            tryAddColumn(st, "player_rpg", "intel INTEGER NOT NULL DEFAULT 1");
            tryAddColumn(st, "player_rpg", "con INTEGER NOT NULL DEFAULT 1");
            tryAddColumn(st, "player_rpg", "cha INTEGER NOT NULL DEFAULT 1");
            tryAddColumn(st, "player_rpg", "sen INTEGER NOT NULL DEFAULT 1");

        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    @Override
    public HylPlayerData loadOrCreate(UUID uuid) {
        HylPlayerData d = load(uuid);
        if (d != null) return d;

        d = new HylPlayerData(uuid);
        // defaults neutres: recalculés par StatsService après
        d.level = 1;
        d.xp = 0;
        d.maxHp = 1;
        d.hp = 1;
        d.maxMana = 0;
        d.mana = 0;
        d.combatUntilMs = 0L;
        d.clamp();

        insert(d);
        return d;
    }

    @Override
    public void save(HylPlayerData d) {
        d.clamp();
        try (PreparedStatement ps = cx.prepareStatement("""
            UPDATE player_rpg
               SET level = ?,
                   xp = ?,
                   max_hp = ?,
                   hp = ?,
                   max_mana = ?,
                   mana = ?,
                   combat_until_ms = ?,
                   str = ?,
                   dex = ?,
                   intel = ?,
                   con = ?,
                   cha = ?,
                   sen = ?
             WHERE uuid = ?
        """)) {
            bindAll(ps, d);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB save failed: " + d.uuid, e);
        }
    }

    // -------------------
    // Internal helpers
    // -------------------

    private HylPlayerData load(UUID uuid) {
        try (PreparedStatement ps = cx.prepareStatement("""
            SELECT uuid, level, xp, max_hp, hp, max_mana, mana, combat_until_ms,
                   str, dex, intel, con, cha, sen
              FROM player_rpg
             WHERE uuid = ?
        """)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                HylPlayerData d = new HylPlayerData(UUID.fromString(rs.getString("uuid")));
                d.level = rs.getInt("level");
                d.xp = rs.getInt("xp");
                d.maxHp = rs.getInt("max_hp");
                d.hp = rs.getInt("hp");
                d.maxMana = rs.getInt("max_mana");
                d.mana = rs.getInt("mana");
                d.combatUntilMs = rs.getLong("combat_until_ms");
                d.str = rs.getInt("str");
                d.dex = rs.getInt("dex");
                d.intel = rs.getInt("intel");
                d.con = rs.getInt("con");
                d.cha = rs.getInt("cha");
                d.sen = rs.getInt("sen");
                d.clamp();
                return d;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB load failed: " + uuid, e);
        }
    }

    private void insert(HylPlayerData d) {
        d.clamp();
        try (PreparedStatement ps = cx.prepareStatement("""
            INSERT INTO player_rpg (uuid, level, xp, max_hp, hp, max_mana, mana, combat_until_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """)) {
            ps.setString(1, d.uuid.toString());
            ps.setInt(2, d.level);
            ps.setInt(3, d.xp);
            ps.setInt(4, d.maxHp);
            ps.setInt(5, d.hp);
            ps.setInt(6, d.maxMana);
            ps.setInt(7, d.mana);
            ps.setLong(8, d.combatUntilMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("DB insert failed: " + d.uuid, e);
        }
    }

    private void bindAll(PreparedStatement ps, HylPlayerData d) throws SQLException {
        ps.setInt(1, d.level);
        ps.setInt(2, d.xp);
        ps.setInt(3, d.maxHp);
        ps.setInt(4, d.hp);
        ps.setInt(5, d.maxMana);
        ps.setInt(6, d.mana);
        ps.setLong(7, d.combatUntilMs);

        ps.setInt(8, d.str);
        ps.setInt(9, d.dex);
        ps.setInt(10, d.intel);
        ps.setInt(11, d.con);
        ps.setInt(12, d.cha);
        ps.setInt(13, d.sen);

        ps.setString(14, d.uuid.toString());    }

    private static void tryAddColumn(Statement st, String table, String columnDef) {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
        } catch (SQLException ignored) {
            // déjà existant
        }
    }
}
