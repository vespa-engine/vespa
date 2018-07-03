// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jonmv
 */
public class MockLogStore implements LogStore {

    private final Map<RunId, Map<String, byte[]>> storage = new ConcurrentHashMap<>();

    @Override
    public byte[] getLog(RunId id, String step) {
        return storage.containsKey(id) && storage.get(id).containsKey(step)
                ? storage.get(id).get(step)
                : new byte[0];
    }

    @Override
    public void setLog(RunId id, String step, byte[] log) {
        storage.putIfAbsent(id, new ConcurrentHashMap<>());
        storage.get(id).put(step, log);
    }

    @Override
    public void deleteTestData(RunId id) {
        storage.remove(id);
    }

}
