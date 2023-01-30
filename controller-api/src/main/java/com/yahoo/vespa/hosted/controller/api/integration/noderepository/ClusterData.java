// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.IntRange;
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

    @JsonProperty("groupSize")
    public IntRangeData groupSize;

    @JsonProperty("current")
    public ClusterResourcesData current;

    @JsonProperty("suggested")
    public AutoscalingData suggested;

    @JsonProperty("target")
    public AutoscalingData target;

    @JsonProperty("scalingEvents")
    public List<ScalingEventData> scalingEvents;

    @JsonProperty("scalingDuration")
    public Long scalingDuration;

    public Cluster toCluster(String id) {
        return new Cluster(ClusterSpec.Id.from(id),
                           ClusterSpec.Type.from(type),
                           min.toClusterResources(),
                           max.toClusterResources(),
                           groupSize == null ? IntRange.empty() : groupSize.toRange(),
                           current.toClusterResources(),
                           target == null ? Cluster.Autoscaling.empty() : target.toAutoscaling(),
                           suggested == null ? Cluster.Autoscaling.empty() : suggested.toAutoscaling(),
                           scalingEvents == null ? List.of()
                                                 : scalingEvents.stream().map(data -> data.toScalingEvent()).toList(),
                           scalingDuration == null ? Duration.ofMillis(0) : Duration.ofMillis(scalingDuration));
    }

}
