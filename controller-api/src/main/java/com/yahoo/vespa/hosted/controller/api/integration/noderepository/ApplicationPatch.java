// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Patchable data under Application
 *
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationPatch {

    @JsonProperty("currentReadShare")
    public Double currentReadShare;

    @JsonProperty("maxReadShare")
    public Double maxReadShare;

    @JsonProperty("clusters")
    public Map<String, ClusterPatch> clusters = new HashMap<>();

    public static class ClusterPatch {

        @JsonProperty("bcpGroupInfo")
        public BcpGroupInfo bcpGroupInfo;

        public ClusterPatch(BcpGroupInfo bcpGroupInfo) {
            this.bcpGroupInfo = bcpGroupInfo;
        }

    }

    public static class BcpGroupInfo {

        @JsonProperty("queryRate")
        public Double queryRate;

        @JsonProperty("growthRateHeadroom")
        public Double growthRateHeadroom;

        @JsonProperty("cpuCostPerQuery")
        public Double cpuCostPerQuery;

        public BcpGroupInfo(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {
            this.queryRate = queryRate;
            this.growthRateHeadroom = growthRateHeadroom;
            this.cpuCostPerQuery = cpuCostPerQuery;
        }

    }

}
