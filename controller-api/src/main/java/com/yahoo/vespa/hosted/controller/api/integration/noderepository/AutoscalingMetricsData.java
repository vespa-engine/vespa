package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoscalingMetricsData {

    @JsonProperty("queryRate")
    public Double queryRate;

    @JsonProperty("growthRateHeadroom")
    public Double growthRateHeadroom;

    @JsonProperty("cpuCostPerQuery")
    public Double cpuCostPerQuery;

    public Cluster.Autoscaling.Metrics toMetrics() {
        return new Cluster.Autoscaling.Metrics(queryRate, growthRateHeadroom, cpuCostPerQuery);
    }

}
