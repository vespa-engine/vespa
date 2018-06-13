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
 * <p>Must be closed on successful start of monitoring ({}
 *
 * <p>Thread-safe
 *
 * @author hakon
 */
public class HealthMonitor implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(HealthMonitor.class.getName());

    /** The duration between each health request. */
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(10);

    // About 'static': Javadoc says "Instances of java.util.Random are threadsafe."
    private static final Random random = new Random();

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final HealthClient healthClient;
    private final Duration delay;

    private volatile HealthInfo lastHealthInfo = HealthInfo.empty();

    public HealthMonitor(HealthEndpoint stateV1HealthEndpoint) {
        this(new HealthClient(stateV1HealthEndpoint), DEFAULT_DELAY);
    }

    /** For testing. */
    HealthMonitor(HealthClient healthClient, Duration delay) {
        this.healthClient = healthClient;
        this.delay = delay;
    }

    public void startMonitoring() {
        healthClient.start();
        executor.scheduleWithFixedDelay(
                this::updateSynchronously,
                initialDelayInMillis(delay.toMillis()),
                delay.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public ServiceStatus getStatus() {
        return lastHealthInfo.toServiceStatus();
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

    private long initialDelayInMillis(long maxInitialDelayInSeconds) {
        return random.nextLong() % maxInitialDelayInSeconds;
    }

    private void updateSynchronously() {
        try {
            lastHealthInfo = healthClient.getHealthInfo();
        } catch (Throwable t) {
            // An uncaught exception will kill the executor.scheduleWithFixedDelay thread!
            logger.log(LogLevel.WARNING, "Failed to get health info for " +
                    healthClient.getEndpoint(), t);
        }
    }
}
