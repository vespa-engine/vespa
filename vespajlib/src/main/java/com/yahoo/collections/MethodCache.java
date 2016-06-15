// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.yahoo.concurrent.CopyOnWriteHashMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created with IntelliJ IDEA.
 * User: balder
 * Date: 6/12/13
 * Time: 9:03 AM
 * To change this template use File | Settings | File Templates.
 */
public final class MethodCache {
    private final String methodName;
    private final CopyOnWriteHashMap<String, Method> cache = new CopyOnWriteHashMap<>();

    public MethodCache(String methodName) {
        this.methodName = methodName;
    }

    public final Method get(Object object) {
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
