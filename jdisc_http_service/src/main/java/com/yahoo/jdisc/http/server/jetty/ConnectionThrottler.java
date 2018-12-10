// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.server.AcceptRateLimit;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LowResourceMonitor;
import org.eclipse.jetty.util.component.LifeCycle;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singleton;

/**
 * Throttles new connections using {@link LowResourceMonitor}, {@link AcceptRateLimit} and {@link ConnectionLimit}.
 *
 * @author bjorncs
 */
class ConnectionThrottler {

    private final Object monitor = new Object();
    private final Queue<Runnable> throttleResetters = new ArrayDeque<>();
    private final Collection<LifeCycle> beans = new ArrayList<>();
    private final Connector connector;
    private int throttlersCount;

    ConnectionThrottler(Connector connector, ConnectorConfig.Throttling config) {
        this.connector = connector;
        Duration idleTimeout = fromSeconds(config.idleTimeout());
        if (config.maxAcceptRate() != -1) {
            beans.add(new CoordinatedAcceptRateLimit(config.maxAcceptRate(), fromSeconds(config.maxAcceptRatePeriod())));
        }
        if (config.maxConnections() != -1) {
            beans.add(new CoordinatedConnectionLimit(config.maxConnections(), idleTimeout));
        }
        if (config.maxHeapUtilization() != -1) {
            beans.add(new CoordinatedLowResourcesLimit(config.maxHeapUtilization(), idleTimeout));
        }
    }

    void registerBeans() {
        beans.forEach(bean -> connector.getServer().addBean(connector));
    }

    private static Duration fromSeconds(double seconds) {
        return Duration.ofMillis((long) (seconds * 1000));
    }

    private void onThrottle(Runnable throttleResetter) {
        synchronized (monitor) {
            ++throttlersCount;
            throttleResetters.offer(throttleResetter);
        }
    }

    private void onReset() {
        List<Runnable> resetters = new ArrayList<>();
        synchronized (monitor) {
            if (--throttlersCount == 0) {
                resetters.addAll(throttleResetters);
                throttleResetters.clear();
            }
        }
        resetters.forEach(Runnable::run);
    }
    private static long toMaxMemoryUsageInBytes(double maxHeapUtilization) {
        return (long) (maxHeapUtilization * Runtime.getRuntime().maxMemory());
    }

    private class CoordinatedLowResourcesLimit extends LowResourceMonitor {

        CoordinatedLowResourcesLimit(double maxHeapUtilization, Duration idleTimeout) {
            super(connector.getServer());
            super.setMonitoredConnectors(singleton(connector));
            super.setMaxMemory(toMaxMemoryUsageInBytes(maxHeapUtilization));
            super.setLowResourcesIdleTimeout((int)idleTimeout.toMillis());
        }

        @Override
        protected void setLowResources() {
            super.setLowResources();
            ConnectionThrottler.this.onThrottle(() -> super.clearLowResources());
        }

        @Override
        protected void clearLowResources() {
            ConnectionThrottler.this.onReset();
        }
    }
    private class CoordinatedConnectionLimit extends ConnectionLimit {

        CoordinatedConnectionLimit(int maxConnections, Duration idleTimeout) {
            super(maxConnections, connector);
            super.setIdleTimeout(idleTimeout.toMillis());
        }

        @Override
        protected void limit() {
            super.limit();
            ConnectionThrottler.this.onThrottle(() -> super.unlimit());
        }

        @Override
        protected void unlimit() {
            ConnectionThrottler.this.onReset();
        }
    }

    private class CoordinatedAcceptRateLimit extends AcceptRateLimit {
        CoordinatedAcceptRateLimit(int limit, Duration period) {
            super(limit, period.toMillis(), TimeUnit.MILLISECONDS, connector);
        }

        @Override
        protected void limit() {
            super.limit();
            ConnectionThrottler.this.onThrottle(() -> super.unlimit());
        }

        @Override
        protected void unlimit() {
            ConnectionThrottler.this.onReset();
        }
    }
}
