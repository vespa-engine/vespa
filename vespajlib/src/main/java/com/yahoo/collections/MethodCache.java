// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.yahoo.concurrent.CopyOnWriteHashMap;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * This will cache methods solved by reflection as reflection is expensive.
 * Note that if the bundle from which the method is removed/changed you might have
 * a problem... A ClassCastException might be one indication. Then clearing the cache and retrying it
 * once to see if it goes away might be a solution.
 * @author baldersheim
 */
public final class MethodCache {

    private final String methodName;
    private final CopyOnWriteHashMap<String, Pair<Class<?>, Method>> cache = new CopyOnWriteHashMap<>();

    public MethodCache(String methodName) {
        this.methodName = methodName;
    }

    /*
     Clear all cached methods. Might be a wise thing to do, if you have cached some methods
     that have changed due to new bundles being reloaded.
    */
    public void clear() {
        cache.clear();
    }

    public Method get(Object object) {
        return get(object, null);
    }

    public Method get(Object object, Consumer<String> onPut) {
        Pair<Class<?>, Method> pair = cache.get(object.getClass().getName());
        // When changing bundles, you might end up having cached the old method pointing to the old bundle.
        // That will then lead to a class cast exception when invoking the wrong clone method.
        // Whenever we detect a new class with the same name, we therefore drop the entire cache. 
        // This is also the reason for caching the pair of method and original class—not just the method.
        if (pair != null && pair.getFirst() != object.getClass()) {
            cache.clear();
            pair = null;
        }
        Method method = pair == null ? null : pair.getSecond();
        if (pair == null) {
            method = lookupMethod(object);
            cache.put(object.getClass().getName(), new Pair<>(object.getClass(), method));
            if (onPut != null)
                onPut.accept(object.getClass().getName());
        }
        return method;
    }

    private Method lookupMethod(Object object)  {
        try {
            return object.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
