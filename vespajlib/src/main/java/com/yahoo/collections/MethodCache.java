// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.yahoo.concurrent.CopyOnWriteHashMap;

import java.lang.reflect.Method;

/**
 * This will cache methods solved by reflection as reflection is expensive.
 * Note that if the bundle from which the method is removed/changed you might have
 * a problem... A ClassCastException might be one indication. Then clearing the cache and retrying it
 * once to see if it goes away might be a solution.
 * @author baldersheim
 */
public final class MethodCache {

    private final String methodName;
    private final CopyOnWriteHashMap<String, Method> cache = new CopyOnWriteHashMap<>();

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
        Method m = cache.get(object.getClass().getName());
        if (m == null) {
            m = lookupMethod(object);
            if (m != null) {
                cache.put(object.getClass().getName(), m);
            }
        }
        return m;
    }
    private Method lookupMethod(Object object)  {
        try {
            return object.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
