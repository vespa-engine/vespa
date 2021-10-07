// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenanceJobList {
    @JsonProperty("jobs")
    public List<MaintenanceJobName> jobs = new ArrayList<>();
    @JsonProperty("inactive")
    public List<String> inactive = new ArrayList<>();
}
