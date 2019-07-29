package com.yahoo.vespa.hosted.controller.api.integration.metrics;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Retrieves metrics from the configuration server.
 *
 * @author ogronnesby
 */
public class ConfigServerMetricsService implements MetricsService {
    private final ConfigServer configServerClient;

    public ConfigServerMetricsService(ConfigServer configServerClient) {
        this.configServerClient = configServerClient;
    }

    @Override
    public ApplicationMetrics getApplicationMetrics(ApplicationId application) {
        // TODO(ogronnesby): How to produce these values in Public context?
        return new ApplicationMetrics(0.0, 0.0);
    }

    @Override
    public DeploymentMetrics getDeploymentMetrics(ApplicationId application, ZoneId zone) {
        var deploymentId = new DeploymentId(application, zone);
        var metrics = configServerClient.getMetrics(deploymentId);

        // TODO(ogronnesby): We probably want something more intelligent than just using .sum(), but it's better to
        // TODO(ogronnesby): get some values populated and then fix the formula later.

        // The field names here come from the MetricsResponse class.

        return new DeploymentMetrics(
                doubleStream(metrics, "queriesPerSecond").mapToDouble(Double::doubleValue).sum(),
                doubleStream(metrics, "feedPerSecond").mapToDouble(Double::doubleValue).sum(),
                doubleStream(metrics, "documentCount").mapToLong(Double::longValue).sum(),
                doubleStream(metrics, "queryLatency").mapToDouble(Double::doubleValue).sum(),
                doubleStream(metrics, "feedLatency").mapToDouble(Double::doubleValue).sum()
        );
    }

    @Override
    public Map<HostName, RotationStatus> getRotationStatus(String rotationName) {
        // TODO(ogronnesby): getRotationStatus doesn't really belong in this interface, and global
        // TODO(ogronnesby): endpoints does not work in public yet.
        return Map.of();
    }

    @Override
    public Map<String, SystemMetrics> getSystemMetrics(ApplicationId application, ZoneId zone) {
        // TODO(ogronnesby): Need a backing source for this data
        return Map.of();
    }

    private Stream<Double> doubleStream(List<ClusterMetrics> metrics, String name) {
        return metrics.stream().map(m -> m.getMetrics().getOrDefault(name, 0.0));
    }
}
