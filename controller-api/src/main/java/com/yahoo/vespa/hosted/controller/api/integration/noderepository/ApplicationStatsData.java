// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationStats;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationStatsData {

    @JsonProperty("id")
    public String id;

    @JsonProperty("load")
    public LoadData load;

    @JsonProperty("cost")
    public Double cost;

    @JsonProperty("unutilizedCost")
    public Double unutilizedCost;

    public ApplicationStats toApplicationStats() {
        return new ApplicationStats(ApplicationId.fromFullString(id), load.toLoad(), cost, unutilizedCost);
    }

}
