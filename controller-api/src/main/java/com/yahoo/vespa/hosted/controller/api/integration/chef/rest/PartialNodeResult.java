// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.chef.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartialNodeResult {
    @JsonProperty("total")
    public int total;
    @JsonProperty("start")
    public int start;
    @JsonProperty("rows")
    public List<PartialNode> rows;
}
