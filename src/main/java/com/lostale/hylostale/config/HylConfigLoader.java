package com.lostale.hylostale.config;

import com.google.gson.Gson;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class HylConfigLoader {
    public static HylConfig load(Path file) {
        try {
            if (!Files.exists(file)) {
                HylConfig def = new HylConfig();
                Files.writeString(file, new Gson().toJson(def));
                return def;
            }

            try (Reader r = Files.newBufferedReader(file)) {
                return new Gson().fromJson(r, HylConfig.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RPG config", e);
        }
    }
}
