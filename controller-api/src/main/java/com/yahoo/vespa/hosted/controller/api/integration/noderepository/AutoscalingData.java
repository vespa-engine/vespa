package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Load;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoscalingData {

    @JsonProperty("status")
    public String status;

    @JsonProperty("description")
    public String description;

    @JsonProperty("resources")
    public ClusterResourcesData resources;

    @JsonProperty("at")
    public Long at;

    @JsonProperty("peak")
    public LoadData peak;

    @JsonProperty("ideal")
    public LoadData ideal;

    @JsonProperty("metrics")
    public AutoscalingMetricsData metrics;

    public Cluster.Autoscaling toAutoscaling() {
        return new Cluster.Autoscaling(status == null ? "" : status,
                                       description == null ? "" : description,
                                       resources == null ? Optional.empty() : Optional.ofNullable(resources.toClusterResources()),
                                       at == null ? Instant.EPOCH : Instant.ofEpochMilli(at),
                                       peak == null ? Load.zero() : peak.toLoad(),
                                       ideal == null ? Load.zero() : ideal.toLoad(),
                                       metrics == null ? Cluster.Autoscaling.Metrics.zero() : metrics.toMetrics());
    }

}
