package com.lostale.hylostale.util.system;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UiQueue {

    private static final Queue<Runnable> Q = new ConcurrentLinkedQueue<>();
    private UiQueue() {}

    public static void post(Runnable r) {
        if (r != null) Q.add(r);
    }

    public static void drain(int max) {
        for (int i = 0; i < max; i++) {
            Runnable r = Q.poll();
            if (r == null) return;
            try { r.run(); } catch (Throwable ignored) {}
        }
    }
}
