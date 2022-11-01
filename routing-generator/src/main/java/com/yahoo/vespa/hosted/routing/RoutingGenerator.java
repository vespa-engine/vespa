// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigHandle;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.jdisc.Metric;
import com.yahoo.routing.config.ZoneConfig;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.routing.nginx.Nginx;
import com.yahoo.vespa.hosted.routing.status.RoutingStatus;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.concurrent.Sleeper;

import java.nio.file.FileSystems;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The routing generator generates a routing table for a hosted Vespa zone.
 *
 * Config is retrieved by subscribing to {@link LbServicesConfig} for all deployments. This is then translated to a
 * {@link RoutingTable}, which is loaded into a {@link Router}.
 *
 * @author oyving
 * @author mpolden
 */
public class RoutingGenerator extends AbstractComponent {

    private static final Logger log = Logger.getLogger(RoutingGenerator.class.getName());
    private static final Duration configTimeout = Duration.ofSeconds(10);
    private static final Duration shutdownTimeout = Duration.ofSeconds(10);
    private static final Duration refreshInterval = Duration.ofSeconds(30);

    private final Router router;
    private final Clock clock;
    private final ConfigSubscriber configSubscriber;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("routing-generator-config-subscriber"));
    private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("routing-generator-maintenance"));
    private final Object monitor = new Object();

    private volatile RoutingTable routingTable = null;

    @Inject
    public RoutingGenerator(ZoneConfig zoneConfig, RoutingStatus routingStatus, Metric metric) {
        this(new ConfigSourceSet(zoneConfig.configserver()), new Nginx(FileSystems.getDefault(),
                                                                       new ProcessExecuter(),
                                                                       Sleeper.DEFAULT,
                                                                       Clock.systemUTC(),
                                                                       routingStatus,
                                                                       metric),
             Clock.systemUTC());
    }

    RoutingGenerator(ConfigSource configSource, Router router, Clock clock) {
        this.router = Objects.requireNonNull(router);
        this.clock = Objects.requireNonNull(clock);
        this.configSubscriber = new ConfigSubscriber(configSource);
        executor.execute(() -> subscribeOn(LbServicesConfig.class, this::load, configSource, executor));
        // Reload configuration periodically. The router depend on state from other sources than config, such as RoutingStatus
        scheduledExecutor.scheduleAtFixedRate(this::reload, refreshInterval.toMillis(), refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Get the currently active routing table, if any */
    public Optional<RoutingTable> routingTable() {
        return Optional.ofNullable(routingTable);
    }

    /** Reload the current routing table, if any */
    private void reload() {
        synchronized (monitor) {
            routingTable().ifPresent(this::load);
        }
    }

    /** Load the given routing table */
    private void load(RoutingTable newTable) {
        synchronized (monitor) {
            router.load(newTable);
            routingTable = newTable;
        }
    }

    private void load(LbServicesConfig lbServicesConfig, long generation) {
        load(RoutingTable.from(lbServicesConfig, generation));
    }

    private <T extends ConfigInstance> void subscribeOn(Class<T> clazz, BiConsumer<T, Long> action, ConfigSource configSource,
                                                        ExecutorService executor) {
        ConfigHandle<T> configHandle = null;
        String configId = "*";
        while (!executor.isShutdown()) {
            try {
                boolean initializing = true;
                log.log(Level.INFO, "Subscribing to configuration " + clazz + "@" + configId + " from " + configSource);
                if (configHandle == null) {
                    configHandle = configSubscriber.subscribe(clazz, configId);
                }
                while (!executor.isShutdown() && !configSubscriber.isClosed()) {
                    Instant subscribingAt = clock.instant();
                    if (configSubscriber.nextGeneration(configTimeout.toMillis(), initializing) && configHandle.isChanged()) {
                        log.log(Level.INFO, "Received new configuration: " + configHandle);
                        T configuration = configHandle.getConfig();
                        log.log(Level.FINE, "Received new configuration: " + configuration);
                        action.accept(configuration, configSubscriber.getGeneration());
                        initializing = false;
                    } else {
                        log.log(Level.FINE, "Configuration tick with no change: " + configHandle +
                                            ", getting config took " + Duration.between(subscribingAt, clock.instant()) +
                                            ", timeout is " + configTimeout);
                    }
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "Exception while subscribing to configuration: " + clazz + "@" + configId +
                                       " from " + configSource + ": " + Exceptions.toMessageString(e));
            }
        }
    }

    @Override
    public void deconstruct() {
        configSubscriber.close();
        // shutdownNow because ConfigSubscriber#nextGeneration blocks until next config, and we don't want to wait for
        // that when shutting down
        executor.shutdownNow();
        scheduledExecutor.shutdown();
        awaitTermination("executor", executor);
        awaitTermination("scheduledExecutor", scheduledExecutor);
    }

    private static void awaitTermination(String name, ExecutorService executorService) {
        try {
            if (!executorService.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Failed to shut down " + name + " within " + shutdownTimeout);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
