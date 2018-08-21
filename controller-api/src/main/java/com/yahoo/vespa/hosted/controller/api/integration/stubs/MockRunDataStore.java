// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.RunDataStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jonmv
 */
public class MockRunDataStore implements RunDataStore {

    private final Map<RunId, byte[]> logs = new ConcurrentHashMap<>();

    @Override
    public byte[] get(RunId id) {
        return logs.getOrDefault(id, new byte[0]);
    }

    @Override
    public void put(RunId id, byte[] log) {
        logs.put(id, log);
    }

    @Override
    public void delete(RunId id) {
        logs.remove(id);
    }

}
