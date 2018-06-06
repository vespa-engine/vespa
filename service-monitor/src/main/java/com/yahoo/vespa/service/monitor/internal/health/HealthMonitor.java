// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ServiceStatus;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Used to monitor the health of a single URL endpoint.
 *
 * @author hakon
 */
public class HealthMonitor implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(HealthMonitor.class.getName());
    private static final Duration DELAY = Duration.ofSeconds(20);
    // About 'static': Javadoc says "Instances of java.util.Random are threadsafe."
    private static final Random random = new Random();

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final HealthClient healthClient;

    private volatile HealthInfo lastHealthInfo = HealthInfo.empty();

    public HealthMonitor(HealthEndpoint stateV1HealthEndpoint) {
        this.healthClient = new HealthClient(stateV1HealthEndpoint);
    }

    /** For testing. */
    HealthMonitor(HealthClient healthClient) {
        this.healthClient = healthClient;
    }

    public void startMonitoring() {
        healthClient.start();
        executor.scheduleWithFixedDelay(
                this::updateSynchronously,
                initialDelayInSeconds(DELAY.getSeconds()),
                DELAY.getSeconds(),
                TimeUnit.SECONDS);
    }

    public ServiceStatus getStatus() {
        // todo: return lastHealthInfo.toServiceStatus();
        return ServiceStatus.NOT_CHECKED;
    }

    @Override
    public void close() {
        executor.shutdown();

        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.log(LogLevel.INFO, "Interrupted while waiting for health monitor termination: " +
                    e.getMessage());
        }

        healthClient.close();
    }

    private long initialDelayInSeconds(long maxInitialDelayInSeconds) {
        return random.nextLong() % maxInitialDelayInSeconds;
    }

    private void updateSynchronously() {
        lastHealthInfo = healthClient.getHealthInfo();
    }
}
