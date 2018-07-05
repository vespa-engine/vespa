// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jonmv
 */
public class MockLogStore implements LogStore {

    private final Map<RunId, Map<String, byte[]>> logs = new ConcurrentHashMap<>();

    @Override
    public byte[] get(RunId id, String step) {
        return logs.getOrDefault(id, Collections.emptyMap()).getOrDefault(step, new byte[0]);
    }

    @Override
    public void append(RunId id, String step, byte[] log) {
        logs.putIfAbsent(id, new ConcurrentHashMap<>());
        byte[] old = get(id, step);
        byte[] union = new byte[old.length + log.length];
        System.arraycopy(old, 0, union, 0, old.length);
        System.arraycopy(log, 0, union, old.length, log.length);
        logs.get(id).put(step, union);
    }

    @Override
    public void delete(RunId id) {
        logs.remove(id);
    }

}
