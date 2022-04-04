// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.nginx;

import com.google.common.collect.ImmutableMap;
import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.routing.RoutingTable;
import com.yahoo.vespa.hosted.routing.status.HealthStatus;
import com.yahoo.vespa.hosted.routing.status.ServerGroup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Report Nginx metrics periodically.
 *
 * @author mortent
 * @author mpolden
 */
public class NginxMetricsReporter extends AbstractComponent implements Runnable {

    private static final Duration interval = Duration.ofSeconds(20);

    static final String UPSTREAM_UP_METRIC = "nginx.upstreams.up";
    static final String UPSTREAM_DOWN_METRIC = "nginx.upstreams.down";
    static final String UPSTREAM_UNKNOWN_METRIC = "nginx.upstreams.unknown";
    static final String CONFIG_AGE_METRIC = "upstreams_configuration_age";

    private final Metric metric;
    private final HealthStatus healthStatus;
    private final ApplicationId routingApplication;
    private final FileSystem fileSystem;
    private final ScheduledExecutorService service;
    private final Supplier<Optional<RoutingTable>> tableSupplier;

    @Inject
    public NginxMetricsReporter(ApplicationIdConfig applicationId, Metric metric, HealthStatus healthStatus, RoutingGenerator routingGenerator) {
        this(ApplicationId.from(applicationId), metric, healthStatus, FileSystems.getDefault(), interval, routingGenerator::routingTable);
    }

    NginxMetricsReporter(ApplicationId application, Metric metric, HealthStatus healthStatus, FileSystem fileSystem, Duration interval,
                         Supplier<Optional<RoutingTable>> tableSupplier) {
        this.metric = Objects.requireNonNull(metric);
        this.healthStatus = Objects.requireNonNull(healthStatus);
        this.routingApplication = Objects.requireNonNull(application);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.tableSupplier = Objects.requireNonNull(tableSupplier);
        this.service = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("nginx-metrics-reporter"));
        this.service.scheduleAtFixedRate(this, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        Optional<RoutingTable> table = tableSupplier.get();
        table.ifPresent(this::reportHealth);
        reportConfigAge();
    }

    private void reportConfigAge() {
        Path temporaryNginxConfiguration = NginxPath.temporaryConfig.in(fileSystem);
        Path nginxConfiguration = NginxPath.config.in(fileSystem);
        Optional<Instant> temporaryConfigModified = lastModified(temporaryNginxConfiguration);
        if (temporaryConfigModified.isEmpty()) {
            metric.set(CONFIG_AGE_METRIC, 0, metric.createContext(Map.of()));
            return;
        }
        Instant configModified = lastModified(nginxConfiguration).orElse(Instant.EPOCH);
        long secondsDiff = Math.abs(Duration.between(configModified, temporaryConfigModified.get()).toSeconds());
        metric.set(CONFIG_AGE_METRIC, secondsDiff, metric.createContext(Map.of()));
    }

    private void reportHealth(RoutingTable table) {
        Collection<RoutingTable.Target> targets = table.asMap().values();
        Map<String, List<ServerGroup.Server>> status = healthStatus.servers().asMap();
        targets.forEach(service -> {
            List<ServerGroup.Server> serversOfUpstream = status.get(service.id());
            if (serversOfUpstream != null) {
                reportMetrics(service, serversOfUpstream);
            } else {
                reportMetricsUnknown(service);
            }
        });

        Set<String> knownUpstreams = targets.stream().map(RoutingTable.Target::id).collect(Collectors.toSet());
        long unknownUpstreamCount = status.keySet().stream()
                                          .filter(upstreamName -> !knownUpstreams.contains(upstreamName))
                                          .count();
        reportMetricsUnknown(unknownUpstreamCount);
    }

    // We report a target as unknown if there is no trace of it in the health check yet.  This might not be an issue
    // (the health check status is a cache), but if it lasts for a long time it might be an error.
    private void reportMetricsUnknown(RoutingTable.Target target) {
        var dimensions = metricsDimensionsForService(target);
        var context = metric.createContext(dimensions);
        metric.set(UPSTREAM_UP_METRIC, 0L, context);
        metric.set(UPSTREAM_DOWN_METRIC, 0L, context);
        metric.set(UPSTREAM_UNKNOWN_METRIC, 1L, context);
    }

    // This happens if an application is mentioned in the health check cache, but is not present
    // in the routing table. We report this to the routing application, as we don't have anywhere
    // else to put the data.
    private void reportMetricsUnknown(long count) {
        var dimensions = ImmutableMap.of(
                "tenantName", routingApplication.tenant().value(),
                "app", String.format("%s.%s", routingApplication.application().value(), routingApplication.instance().value()),
                "applicationId", routingApplication.toFullString(),
                "clusterid", "routing"
        );
        var context = metric.createContext(dimensions);
        metric.set(UPSTREAM_UNKNOWN_METRIC, count, context);
    }

    private void reportMetrics(RoutingTable.Target target, List<ServerGroup.Server> servers) {
        long up = countStatus(servers, true);
        long down = countStatus(servers, false);

        var dimensions = metricsDimensionsForService(target);
        var context = metric.createContext(dimensions);
        metric.set(UPSTREAM_UP_METRIC, up, context);
        metric.set(UPSTREAM_DOWN_METRIC, down, context);
        metric.set(UPSTREAM_UNKNOWN_METRIC, 0L, context);
    }

    private Map<String, String> metricsDimensionsForService(RoutingTable.Target target) {
        String applicationId = target.tenant().value() + "." + target.application().value();
        String app = target.application().value();
        if (target.instance().isPresent()) {
            app += "." + target.instance().get().value();
            applicationId += "." + target.instance().get().value();
        }
        return ImmutableMap.of(
                "tenantName", target.tenant().value(),
                "app", app,
                "applicationId", applicationId,
                "clusterid", target.cluster().value()
        );
    }

    private long countStatus(List<ServerGroup.Server> upstreams, boolean up) {
        return upstreams.stream().filter(nginxServer -> up == nginxServer.up()).count();
    }

    private static Optional<Instant> lastModified(Path path) {
        try {
            return Optional.ofNullable(Files.getLastModifiedTime(path).toInstant());
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deconstruct() {
        Duration timeout = Duration.ofSeconds(10);
        service.shutdown();
        try {
            if (!service.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Failed to shutdown executor within " + timeout);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
