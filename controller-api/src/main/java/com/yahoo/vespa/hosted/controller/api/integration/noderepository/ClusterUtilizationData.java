// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;

/**
 * Utilization ratios
 *
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterUtilizationData {

    @JsonProperty("idealCpu")
    public Double idealCpu;
    @JsonProperty("peakCpu")
    public Double peakCpu;

    @JsonProperty("idealMemory")
    public Double idealMemory;
    @JsonProperty("peakMemory")
    public Double peakMemory;

    @JsonProperty("idealDisk")
    public Double idealDisk;
    @JsonProperty("peakDisk")
    public Double peakDisk;

    public Cluster.Utilization toClusterUtilization() {
        return new Cluster.Utilization(idealCpu, peakCpu, idealMemory, peakMemory, idealDisk, peakDisk);
    }

}
