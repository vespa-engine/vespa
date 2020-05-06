// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;

import java.util.Optional;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterData {

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

    public Cluster toCluster(String id) {
        return new Cluster(ClusterSpec.Id.from(id),
                           min.toClusterResources(),
                           max.toClusterResources(),
                           current.toClusterResources(),
                           target == null ? Optional.empty() : Optional.of(target.toClusterResources()),
                           suggested == null ? Optional.empty() : Optional.of(suggested.toClusterResources()));
    }

}
