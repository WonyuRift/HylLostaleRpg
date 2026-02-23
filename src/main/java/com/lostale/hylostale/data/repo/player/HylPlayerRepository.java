package com.lostale.hylostale.data.repo.player;

import com.lostale.hylostale.data.player.HylPlayerData;

import java.util.UUID;

public interface HylPlayerRepository {
    HylPlayerData loadOrCreate(UUID uuid);
    void save(HylPlayerData data);
}