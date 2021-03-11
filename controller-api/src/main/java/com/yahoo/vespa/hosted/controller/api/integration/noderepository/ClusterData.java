// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterData {

    @JsonProperty("type")
    public String type;
    @JsonProperty("min")
    public ClusterResourcesData min;
    @JsonProperty("max")
    public ClusterResourcesData max;
    @JsonProperty("current")
    public ClusterResourcesData current;
    @JsonProperty("suggested")
    public ClusterResourcesData suggested;
    @JsonProperty("target")
    public ClusterResourcesData target;
    @JsonProperty("utilization")
    public ClusterUtilizationData utilization;
    @JsonProperty("scalingEvents")
    public List<ScalingEventData> scalingEvents;
    @JsonProperty("autoscalingStatus")
    public String autoscalingStatus;
    @JsonProperty("scalingDuration")
    public Long scalingDuration;
    @JsonProperty("maxQueryGrowthRate")
    public Double maxQueryGrowthRate;
    @JsonProperty("currentQueryFractionOfMax")
    public Double currentQueryFractionOfMax;

    public Cluster toCluster(String id) {
        return new Cluster(ClusterSpec.Id.from(id),
                           ClusterSpec.Type.from(type),
                           min.toClusterResources(),
                           max.toClusterResources(),
                           current.toClusterResources(),
                           target == null ? Optional.empty() : Optional.of(target.toClusterResources()),
                           suggested == null ? Optional.empty() : Optional.of(suggested.toClusterResources()),
                           utilization == null ? Cluster.Utilization.empty() : utilization.toClusterUtilization(),
                           scalingEvents == null ? List.of()
                                                 : scalingEvents.stream().map(data -> data.toScalingEvent()).collect(Collectors.toList()),
                           autoscalingStatus,
                           Duration.ofMillis(scalingDuration),
                           maxQueryGrowthRate,
                           currentQueryFractionOfMax);
    }

}
