package com.lostale.hylostale.service.mob;

public class HylZoneLevelService {

    private final long worldSeed;     // stable (seed serveur/monde)
    private final int cellSize;       // ex: 64
    private final int minLvl;
    private final int maxLvl;

    public HylZoneLevelService(long worldSeed, int cellSize, int minLvl, int maxLvl) {
        this.worldSeed = worldSeed;
        this.cellSize = Math.max(8, cellSize);
        this.minLvl = Math.max(1, minLvl);
        this.maxLvl = Math.max(this.minLvl, maxLvl);
    }

    public int getLevelForPosition(double x, double z, long entityKey) {
        int cx = floorDiv((int)Math.floor(x), cellSize);
        int cz = floorDiv((int)Math.floor(z), cellSize);

        long h = mix64(worldSeed);
        h ^= mix64(cx * 0x9E3779B9L);
        h ^= mix64(cz * 0xC2B2AE3DL);

        int span = (maxLvl - minLvl) + 1;
        int base = minLvl + (int)Math.floorMod(h, span);

        // jitter par entité (±2 par défaut)
        long e = mix64(entityKey ^ worldSeed);
        int jitter = (int)Math.floorMod(e, 5) - 2; // [-2..+2]

        int lvl = base + jitter;
        if (lvl < minLvl) lvl = minLvl;
        if (lvl > maxLvl) lvl = maxLvl;
        return lvl;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
