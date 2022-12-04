// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationData {

    @JsonProperty
    public String id;
    @JsonProperty
    public Map<String, ClusterData> clusters;

    public Application toApplication() {
        return new Application(ApplicationId.fromFullString(id),
                               clusters.entrySet().stream().map(e -> e.getValue().toCluster(e.getKey())).collect(Collectors.toList()));
    }

}
