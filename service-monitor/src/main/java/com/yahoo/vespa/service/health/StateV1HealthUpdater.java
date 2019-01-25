// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @author hakonhall
 */
class StateV1HealthUpdater implements HealthUpdater {
    private final StateV1HealthClient healthClient;

    private volatile ServiceStatusInfo serviceStatusInfo = new ServiceStatusInfo(ServiceStatus.NOT_CHECKED);

    StateV1HealthUpdater(URL url, Duration requestTimeout, Duration connectionKeepAlive) {
        this(new StateV1HealthClient(url, requestTimeout, connectionKeepAlive));
    }

    StateV1HealthUpdater(StateV1HealthClient healthClient) {
        this.healthClient = healthClient;
    }

    @Override
    public ServiceStatusInfo getServiceStatusInfo() {
        return serviceStatusInfo;
    }

    @Override
    public void run() {
        // Get time before fetching rather than after, to make the resulting age be an upper limit.
        Instant now = Instant.now();

        HealthInfo healthInfo;
        try {
            healthInfo = healthClient.get();
        } catch (Exception e) {
            healthInfo = HealthInfo.fromException(e);
        }

        ServiceStatus newServiceStatus = healthInfo.isHealthy() ? ServiceStatus.UP : ServiceStatus.DOWN;
        Optional<Instant> newSince = newServiceStatus == serviceStatusInfo.serviceStatus() ?
                serviceStatusInfo.since() : Optional.of(now);

        serviceStatusInfo = new ServiceStatusInfo(newServiceStatus, newSince, Optional.of(now), healthInfo.getErrorDescription());
    }

    @Override
    public void close() {
        healthClient.close();
    }
}
