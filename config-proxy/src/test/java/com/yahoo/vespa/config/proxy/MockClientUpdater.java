// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;

import java.time.Duration;
import java.time.Instant;

class MockClientUpdater extends ClientUpdater {
    private RawConfig lastConfig;

    MockClientUpdater(MemoryCache memoryCache) {
        this(new ConfigProxyStatistics(), new Mode(), memoryCache);
    }

    private MockClientUpdater(ConfigProxyStatistics statistics, Mode mode, MemoryCache memoryCache) {
        super(memoryCache, new MockRpcServer(), statistics, new DelayedResponses(statistics), mode);
    }

    @Override
    public synchronized void updateSubscribers(RawConfig newConfig) {
        lastConfig = newConfig;
    }

    synchronized RawConfig getLastConfig() {
        return lastConfig;
    }

    long waitForConfigGeneration(ConfigKey<?> configKey, long expectedGeneration) {
        Instant end = Instant.now().plus(Duration.ofSeconds(60));
        RawConfig lastConfig;
        do {
            lastConfig = getLastConfig();
            System.out.println("config=" + lastConfig + (lastConfig == null ? "" : ",generation=" + lastConfig.getGeneration()));
            if (lastConfig != null && lastConfig.getKey().equals(configKey) &&
                    lastConfig.getGeneration() == expectedGeneration) {
                break;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (Instant.now().isBefore(end));

        if (lastConfig == null || ! lastConfig.getKey().equals(configKey) || lastConfig.getGeneration() != expectedGeneration)
            throw new RuntimeException("Did not get config " + configKey + " with generation " + expectedGeneration);
        return lastConfig.getGeneration();
    }
}
