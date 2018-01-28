// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.provider;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ThreadSafe
public class DebugHandlerHelper implements NodeAdminDebugHandler {
    private Object monitor = new Object();
    private final ConcurrentMap<String, Supplier<Object>> suppliers = new ConcurrentHashMap<>();

    public void addThreadSafeSupplier(String name, Supplier<Object> threadSafeSupplier) {
        Supplier<Object> previousSupplier = suppliers.putIfAbsent(name, threadSafeSupplier);
        if (previousSupplier != null) {
            throw new IllegalArgumentException(name + " is already registered");
        }
    }

    public void addHandler(String name, NodeAdminDebugHandler handler) {
        addThreadSafeSupplier(name, () -> handler.getDebugPage());
    }

    public void addConstant(String name, String value) {
        addThreadSafeSupplier(name, () -> value);
    }

    public void remove(String name) {
        Supplier<Object> supplier = suppliers.remove(name);
        if (supplier == null) {
            throw new IllegalArgumentException(name + " is not registered");
        }
    }

    @Override
    public Map<String, Object> getDebugPage() {
        return suppliers.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()));
    }
}
