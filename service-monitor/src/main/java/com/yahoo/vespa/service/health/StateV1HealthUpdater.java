// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import java.net.URL;
import java.time.Duration;

/**
 * @author hakonhall
 */
class StateV1HealthUpdater implements HealthUpdater {
    private final StateV1HealthClient healthClient;

    private volatile HealthInfo lastHealthInfo = HealthInfo.empty();

    StateV1HealthUpdater(URL url, Duration requestTimeout, Duration connectionKeepAlive) {
        this(new StateV1HealthClient(url, requestTimeout, connectionKeepAlive));
    }

    StateV1HealthUpdater(StateV1HealthClient healthClient) {
        this.healthClient = healthClient;
    }

    @Override
    public HealthInfo getLatestHealthInfo() {
        return lastHealthInfo;
    }

    @Override
    public void run() {
        try {
            lastHealthInfo = healthClient.get();
        } catch (Exception e) {
            lastHealthInfo = HealthInfo.fromException(e);
        }
    }

    @Override
    public void close() {
        healthClient.close();
    }
}
