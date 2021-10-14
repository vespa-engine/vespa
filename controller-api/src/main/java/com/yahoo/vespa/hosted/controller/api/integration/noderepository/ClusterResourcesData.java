// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ClusterResources;

import java.util.Optional;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterResourcesData {

    @JsonProperty
    public int nodes;
    @JsonProperty
    public int groups;
    @JsonProperty
    public NodeResources resources;

    public ClusterResources toClusterResources() {
        return new ClusterResources(nodes, groups, resources.toNodeResources());
    }

}
