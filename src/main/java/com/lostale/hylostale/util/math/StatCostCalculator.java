package com.lostale.hylostale.util.math;

public final class StatCostCalculator {
    public int costToIncrease(int currentValue) {
        int v = Math.max(1, currentValue);
        if (v >= 99) {
            return Integer.MAX_VALUE;
        } else {
            int step = (v - 2) / 10;
            if (step < 0) {
                step = 0;
            }

            return 2 + step;
        }
    }
}
