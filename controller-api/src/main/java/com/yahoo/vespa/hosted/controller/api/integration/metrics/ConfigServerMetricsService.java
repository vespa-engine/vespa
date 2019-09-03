package com.yahoo.vespa.hosted.controller.api.integration.metrics;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Retrieves metrics from the configuration server.
 *
 * @author ogronnesby
 */
// TODO: This module should not contain components. Move this to controller-server.
public class ConfigServerMetricsService implements MetricsService {

    private final ConfigServer configServerClient;

    @Inject
    public ConfigServerMetricsService(ServiceRegistry serviceRegistry) {
        this(serviceRegistry.configServer());
    }

    ConfigServerMetricsService(ConfigServer configServer) {
        this.configServerClient = configServer;
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

        // The field names here come from the MetricsResponse class.
        return new DeploymentMetrics(
                metrics.stream().flatMap(m -> m.queriesPerSecond().stream()).mapToDouble(Double::doubleValue).sum(),
                metrics.stream().flatMap(m -> m.feedPerSecond().stream()).mapToDouble(Double::doubleValue).sum(),
                metrics.stream().flatMap(m -> m.documentCount().stream()).mapToLong(Double::longValue).sum(),
                weightedAverageLatency(metrics, ClusterMetrics::queriesPerSecond, ClusterMetrics::queryLatency),
                weightedAverageLatency(metrics, ClusterMetrics::feedPerSecond, ClusterMetrics::feedLatency)
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

    private double weightedAverageLatency(List<ClusterMetrics> metrics,
                                          Function<ClusterMetrics, Optional<Double>> rateExtractor,
                                          Function<ClusterMetrics, Optional<Double>> latencyExtractor)
    {
        var rateSum = metrics.stream().flatMap(m -> rateExtractor.apply(m).stream()).mapToDouble(Double::longValue).sum();
        if (rateSum == 0) {
            return 0.0;
        }

        var weightedLatency = metrics.stream()
                .flatMap(m -> {
                    return latencyExtractor.apply(m).flatMap(l -> rateExtractor.apply(m).map(r -> l * r)).stream();
                })
                .mapToDouble(Double::doubleValue)
                .sum();

        return weightedLatency / rateSum;
    }
}
