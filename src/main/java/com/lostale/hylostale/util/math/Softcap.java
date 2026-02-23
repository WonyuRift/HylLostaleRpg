package com.lostale.hylostale.util.math;

public final class Softcap {
    private Softcap() {
    }

    public static double softcap(double x, double k) {
        if (k <= 0.0D) {
            return 0.0D;
        } else {
            return x <= 0.0D ? 0.0D : x / (1.0D + x / k);
        }
    }
}
