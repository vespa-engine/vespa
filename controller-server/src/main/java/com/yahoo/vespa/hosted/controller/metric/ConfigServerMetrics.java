// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.metric;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Retrieves metrics from the configuration server.
 *
 * @author ogronnesby
 */
public class ConfigServerMetrics {

    private final ConfigServer configServer;

    public ConfigServerMetrics(ConfigServer configServer) {
        this.configServer = configServer;
    }

    public ApplicationMetrics getApplicationMetrics(ApplicationId application) {
        // TODO(ogronnesby): How to produce these values in Public context?
        return new ApplicationMetrics(0.0, 0.0);
    }

    public DeploymentMetrics getDeploymentMetrics(ApplicationId application, ZoneId zone) {
        var deploymentId = new DeploymentId(application, zone);
        var metrics = configServer.getMetrics(deploymentId);

        // The field names here come from the MetricsResponse class.
        return new DeploymentMetrics(
                metrics.stream().flatMap(m -> m.queriesPerSecond().stream()).mapToDouble(Double::doubleValue).sum(),
                metrics.stream().flatMap(m -> m.feedPerSecond().stream()).mapToDouble(Double::doubleValue).sum(),
                metrics.stream().flatMap(m -> m.documentCount().stream()).mapToLong(Double::longValue).sum(),
                weightedAverageLatency(metrics, ClusterMetrics::queriesPerSecond, ClusterMetrics::queryLatency),
                weightedAverageLatency(metrics, ClusterMetrics::feedPerSecond, ClusterMetrics::feedLatency)
        );
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
