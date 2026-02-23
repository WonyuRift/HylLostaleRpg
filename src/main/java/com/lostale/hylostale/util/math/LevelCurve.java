package com.lostale.hylostale.util.math;

public final class LevelCurve {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 99;
    private static final long A = 50L;
    private static final long B = 150L;
    private static final long C = 200L;

    public int maxLevel() {
        return 99;
    }

    public int minLevel() {
        return 1;
    }

    public long xpRequiredForLevel(int level) {
        this.validateLevel(level);
        if (level >= 99) {
            return 0L;
        } else {
            long l = (long)level;
            return 50L * l * l + 150L * l + 200L;
        }
    }

    public long totalXpToReachLevel(int level) {
        this.validateLevel(level);
        long total = 0L;

        for(int l = 1; l < level; ++l) {
            total += this.xpRequiredForLevel(l);
        }

        return total;
    }

    private void validateLevel(int level) {
        if (level < 1 || level > 99) {
            throw new IllegalArgumentException("level out of range: " + level + " (allowed 1..99)");
        }
    }
}
