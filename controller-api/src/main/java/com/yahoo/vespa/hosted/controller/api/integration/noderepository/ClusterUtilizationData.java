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

    @JsonProperty("cpu")
    public Double cpu;
    @JsonProperty("idealCpu")
    public Double idealCpu;
    @JsonProperty("currentCpu")
    public Double currentCpu;
    @JsonProperty("peakCpu")
    public Double peakCpu;

    @JsonProperty("memory")
    public Double memory;
    @JsonProperty("idealMemory")
    public Double idealMemory;
    @JsonProperty("currentMemory")
    public Double currentMemory;
    @JsonProperty("peakMemory")
    public Double peakMemory;

    @JsonProperty("disk")
    public Double disk;
    @JsonProperty("idealDisk")
    public Double idealDisk;
    @JsonProperty("currentDisk")
    public Double currentDisk;
    @JsonProperty("peakDisk")
    public Double peakDisk;

    public Cluster.Utilization toClusterUtilization() {
        return new Cluster.Utilization(cpu, idealCpu, currentCpu, peakCpu,
                                       memory, idealMemory, currentMemory, peakMemory,
                                       disk, idealDisk, currentDisk, peakDisk);
    }

}
