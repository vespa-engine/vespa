// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.executor.Cancellable;
import com.yahoo.vespa.service.executor.RunletExecutor;

import java.time.Duration;

/**
 * Used to monitor the health of a single URL endpoint.
 *
 * @author hakon
 */
class StateV1HealthMonitor implements HealthMonitor {

    private final StateV1HealthUpdater updater;
    private final Cancellable periodicExecution;

    StateV1HealthMonitor(StateV1HealthUpdater updater, RunletExecutor executor, Duration delay) {
        this.updater = updater;
        this.periodicExecution = executor.scheduleWithFixedDelay(updater, delay);
    }

    @Override
    public ServiceStatusInfo getStatus() {
        return updater.getServiceStatusInfo();
    }

    @Override
    public void close() {
        periodicExecution.cancel();
    }

}
