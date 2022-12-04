// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepoStats;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeRepoStatsData {

    @JsonProperty("totalCost")
    public Double totalCost;

    @JsonProperty("totalAllocatedCost")
    public Double totalAllocatedCost;

    @JsonProperty("load")
    public LoadData load;

    @JsonProperty("activeLoad")
    public LoadData activeLoad;

    @JsonProperty("applications")
    public List<ApplicationStatsData> applications;

    public NodeRepoStats toNodeRepoStats() {
        return new NodeRepoStats(totalCost, totalAllocatedCost,
                                 load.toLoad(), activeLoad.toLoad(),
                                 applications.stream().map(stats -> stats.toApplicationStats()).collect(Collectors.toList()));
    }

}
