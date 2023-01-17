// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.http.ConnectorConfig;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.LowResourceMonitor;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.statistic.RateStatistic;
import org.eclipse.jetty.util.thread.Scheduler;

import java.nio.channels.SelectableChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Monitor various resource constraints and throttles new connections once a threshold is exceeded.
 * Implementation inspired by Jetty's {@link LowResourceMonitor}, {@link AcceptRateLimit} and {@link ConnectionLimit}.
 *
 * @author bjorncs
 */
@ManagedObject("Monitor various resource constraints and throttles new connections once a threshold is exceeded")
class ConnectionThrottler extends ContainerLifeCycle implements SelectorManager.AcceptListener {

    private static final Logger log = Logger.getLogger(ConnectionThrottler.class.getName());

    private final Object monitor = new Object();
    private final Collection<ResourceLimit> resourceLimits = new ArrayList<>();
    private final AbstractConnector connector;
    private final Duration idleTimeout;
    private final Scheduler scheduler;

    private boolean isRegistered = false;
    private boolean isThrottling = false;

    ConnectionThrottler(AbstractConnector connector, ConnectorConfig.Throttling config) {
        this(Runtime.getRuntime(), new RateStatistic(1, TimeUnit.SECONDS), connector.getScheduler(), connector, config);
    }

    // Intended for unit testing
    ConnectionThrottler(Runtime runtime,
                        RateStatistic rateStatistic,
                        Scheduler scheduler,
                        AbstractConnector connector,
                        ConnectorConfig.Throttling config) {
        this.connector = connector;
        if (config.maxHeapUtilization() != -1) {
            this.resourceLimits.add(new HeapResourceLimit(runtime, config.maxHeapUtilization()));
        }
        if (config.maxConnections() != -1) {
            this.resourceLimits.add(new ConnectionLimitThreshold(config.maxConnections()));
        }
        if (config.maxAcceptRate() != -1) {
            this.resourceLimits.add(new AcceptRateLimit(rateStatistic, config.maxAcceptRate()));
        }
        this.idleTimeout = config.idleTimeout() != -1 ? Duration.ofMillis((long) (config.idleTimeout()*1000)) : null;
        this.scheduler = scheduler;
    }

    void registerWithConnector() {
        synchronized (monitor) {
            if (isRegistered) return;
            isRegistered = true;
            resourceLimits.forEach(connector::addBean);
            connector.addBean(this);
        }
    }

    @Override
    public void onAccepting(SelectableChannel channel) {
        throttleIfAnyThresholdIsExceeded();
    }

    private void throttleIfAnyThresholdIsExceeded() {
        synchronized (monitor) {
            if (isThrottling) return;
            List<String> reasons = getThrottlingReasons();
            if (reasons.isEmpty()) return;
            log.warning(String.format("Throttling new connection. Reasons: %s", reasons));
            isThrottling = true;
            if (connector.isAccepting()) {
                connector.setAccepting(false);
            }
            if (idleTimeout != null) {
                log.warning(String.format("Applying idle timeout to existing connections: timeout=%sms", idleTimeout));
                connector.getConnectedEndPoints()
                        .forEach(endPoint -> endPoint.setIdleTimeout(idleTimeout.toMillis()));
            }
            scheduler.schedule(this::unthrottleIfBelowThresholds, 1, TimeUnit.SECONDS);
        }
    }

    private void unthrottleIfBelowThresholds() {
        synchronized (monitor) {
            if (!isThrottling) return;
            List<String> reasons = getThrottlingReasons();
            if (!reasons.isEmpty()) {
                log.warning(String.format("Throttling continued. Reasons: %s", reasons));
                scheduler.schedule(this::unthrottleIfBelowThresholds, 1, TimeUnit.SECONDS);
                return;
            }
            if (idleTimeout != null) {
                long originalTimeout = connector.getIdleTimeout();
                log.info(String.format("Reverting idle timeout for existing connections: timeout=%sms", originalTimeout));
                connector.getConnectedEndPoints()
                        .forEach(endPoint -> endPoint.setIdleTimeout(originalTimeout));
            }
            log.info("Throttling disabled - resource thresholds no longer exceeded");
            if (!connector.isAccepting()) {
                connector.setAccepting(true);
            }
            isThrottling = false;
        }
    }

    private List<String> getThrottlingReasons() {
        synchronized (monitor) {
            return resourceLimits.stream()
                    .map(ResourceLimit::isThresholdExceeded)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        }
    }

    private interface ResourceLimit extends LifeCycle, SelectorManager.AcceptListener, Connection.Listener {
        /**
         * @return A string containing the reason if threshold exceeded, empty otherwise.
         */
        Optional<String> isThresholdExceeded();

        @Override default void onOpened(Connection connection) {}

        @Override default void onClosed(Connection connection) {}
    }

    /**
     * Note: implementation inspired by Jetty's {@link LowResourceMonitor}
     */
    private static class HeapResourceLimit extends AbstractLifeCycle implements ResourceLimit {
        private final Runtime runtime;
        private final double maxHeapUtilization;

        HeapResourceLimit(Runtime runtime, double maxHeapUtilization) {
            this.runtime = runtime;
            this.maxHeapUtilization = maxHeapUtilization;
        }

        @Override
        public Optional<String> isThresholdExceeded() {
            double heapUtilization = (runtime.maxMemory() - runtime.freeMemory()) / (double) runtime.maxMemory();
            if (heapUtilization > maxHeapUtilization) {
                return Optional.of(String.format("Max heap utilization exceeded: %f%%>%f%%", heapUtilization*100, maxHeapUtilization*100));
            }
            return Optional.empty();
        }
    }

    /**
     * Note: implementation inspired by Jetty's {@link org.eclipse.jetty.server.AcceptRateLimit}
     */
    private static class AcceptRateLimit extends AbstractLifeCycle implements ResourceLimit {
        private final Object monitor = new Object();
        private final RateStatistic rateStatistic;
        private final int maxAcceptRate;

        AcceptRateLimit(RateStatistic rateStatistic, int maxAcceptRate) {
            this.rateStatistic = rateStatistic;
            this.maxAcceptRate = maxAcceptRate;
        }

        @Override
        public Optional<String> isThresholdExceeded() {
            synchronized (monitor) {
                int acceptRate = rateStatistic.getRate();
                if (acceptRate > maxAcceptRate) {
                    return Optional.of(String.format("Max accept rate exceeded: %d>%d", acceptRate, maxAcceptRate));
                }
                return Optional.empty();
            }
        }

        @Override
        public void onAccepting(SelectableChannel channel) {
            synchronized (monitor) {
                rateStatistic.record();
            }
        }

        @Override
        protected void doStop() {
            synchronized (monitor) {
                rateStatistic.reset();
            }
        }
    }

    /**
     * Note: implementation inspired by Jetty's {@link ConnectionLimit}.
     */
    private static class ConnectionLimitThreshold extends AbstractLifeCycle implements ResourceLimit {
        private final Object monitor = new Object();
        private final int maxConnections;
        private final Set<SelectableChannel> connectionsAccepting = new HashSet<>();
        private int connectionOpened;

        ConnectionLimitThreshold(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        @Override
        public Optional<String> isThresholdExceeded() {
            synchronized (monitor) {
                int totalConnections = connectionOpened + connectionsAccepting.size();
                if (totalConnections > maxConnections) {
                    return Optional.of(String.format("Max connection exceeded: %d>%d", totalConnections, maxConnections));
                }
                return Optional.empty();
            }
        }

        @Override
        public void onOpened(Connection connection) {
            synchronized (monitor) {
                connectionsAccepting.remove(connection.getEndPoint().getTransport());
                ++connectionOpened;
            }
        }

        @Override
        public void onClosed(Connection connection) {
            synchronized (monitor) {
                --connectionOpened;
            }
        }

        @Override
        public void onAccepting(SelectableChannel channel) {
            synchronized (monitor) {
                connectionsAccepting.add(channel);
            }

        }

        @Override
        public void onAcceptFailed(SelectableChannel channel, Throwable cause) {
            synchronized (monitor) {
                connectionsAccepting.remove(channel);
            }
        }

        @Override
        protected void doStop() {
            synchronized (monitor) {
                connectionsAccepting.clear();
                connectionOpened = 0;
            }
        }
    }
}
