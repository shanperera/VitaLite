package com.tonic.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticIntFinder {
    private static final Map<Class<?>, Map<Integer, String>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns "pkg.Outer.Inner.FIELD"
     * builds cache on first call per class.
     */
    public static String find(Class<?> root, int target) {
        if (root == null) return null;
        Map<Integer, String> cache = CLASS_CACHE.computeIfAbsent(root, StaticIntFinder::buildCache);
        String result = cache.get(target);
        return result != null ? result : String.valueOf(target);
    }

    /**
     * Builds complete cache for a class and all its inner classes
     */
    private static Map<Integer, String> buildCache(Class<?> root) {
        final Map<Integer, String> cache = new HashMap<>(256); // Size hint for large classes
        final Deque<Class<?>> stack = new ArrayDeque<>(8);
        stack.push(root);

        while (!stack.isEmpty()) {
            final Class<?> cls = stack.pop();
            Class<?>[] inners;
            try {
                inners = cls.getDeclaredClasses();
            } catch (Throwable t) {
                inners = null;
            }
            if (inners != null) {
                for (Class<?> inner : inners) {
                    stack.push(inner);
                }
            }
            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }

            final int classMods = cls.getModifiers();
            final boolean classPublic = Modifier.isPublic(classMods);

            for (final Field f : fields) {
                final int mods = f.getModifiers();
                if ((mods & Modifier.STATIC) == 0) continue;
                if (f.getType() != int.class) continue;
                if (f.isSynthetic()) continue;

                Integer value = null;

                if (classPublic && Modifier.isPublic(mods)) {
                    try {
                        value = f.getInt(null);
                    } catch (IllegalAccessException ignored) {}
                }

                if (value == null) {
                    if (!f.canAccess(null)) {
                        if (!f.trySetAccessible()) {
                            continue;
                        }
                    }
                    try {
                        value = f.getInt(null);
                    } catch (Throwable ignored) {
                        continue;
                    }
                }

                if (!cache.containsKey(value)) {
                    cache.put(value, qualify(root, cls, f.getName()));
                }
            }
        }

        return cache;
    }

    private static String qualify(Class<?> root, Class<?> cls, String fieldName) {
        final String n = cls.getName();
        final String r = root.getName();
        String simple = n.indexOf('$') >= 0 ? n.replace('$', '.') : n;
        String simpleRoot = r.indexOf('$') >= 0 ? r.replace('$', '.') : r;
        return (simple + "." + fieldName).replace(simpleRoot + ".", "");
    }

    /**
     * Clear cache for a specific class (useful if class is reloaded)
     */
    public static void clearCache(Class<?> clazz) {
        CLASS_CACHE.remove(clazz);
    }

    /**
     * Clear entire cache
     */
    public static void clearAllCaches() {
        CLASS_CACHE.clear();
    }

    /**
     * Pre-warm cache for a class (optional - useful during startup)
     */
    public static void warmCache(Class<?> clazz) {
        if (clazz != null && !CLASS_CACHE.containsKey(clazz)) {
            CLASS_CACHE.put(clazz, buildCache(clazz));
        }
    }
}