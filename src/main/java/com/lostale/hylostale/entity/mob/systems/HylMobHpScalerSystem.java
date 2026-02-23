package com.lostale.hylostale.entity.mob.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.lostale.hylostale.data.mob.HylMobData;

import java.lang.reflect.Method;

public class HylMobHpScalerSystem {

    public static void apply(Store<EntityStore> store, CommandBuffer<EntityStore> cb, Ref<EntityStore> mobRef, HylMobData data) {

        EntityStatMap map = store.getComponent(mobRef, EntityStatMap.getComponentType());
        if (map == null) return;

        int healthType = DefaultEntityStatTypes.getHealth();
        EntityStatValue v = map.get(healthType);
        if (v == null) return;

        float baseMax = v.getMax();
        float newMax = baseMax * (1f + 0.08f * Math.max(0, data.level - 1));

        // 1) set max (selon build)
        if (!invokeFirst(map,
                new Object[]{healthType, newMax},
                "setMaxStatValue", "setStatMax", "setMaxValue", "setMax")) {

            // si pas de setter max, abandon propre
            return;
        }

        // 2) heal full à ce nouveau max (option)
        invokeFirst(map,
                new Object[]{healthType, newMax},
                "setStatValue", "setValue", "set");

        // 3) write back via CommandBuffer
        cb.putComponent(mobRef, EntityStatMap.getComponentType(), map);
    }

    private static boolean invokeFirst(Object target, Object[] args, String... names) {
        for (String n : names) {
            try {
                Method m = find(target.getClass(), n, args);
                if (m == null) continue;
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static Method find(Class<?> c, String name, Object[] args) {
        Method[] ms = c.getMethods();
        outer:
        for (Method m : ms) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != args.length) continue;
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (args[i] == null) continue;
                if (!wrap(ps[i]).isAssignableFrom(wrap(args[i].getClass()))) continue outer;
            }
            return m;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == long.class) return Long.class;
        return t;
    }

}
