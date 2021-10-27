// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author hakonhall
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class ApplicationServices {
    @JsonProperty("services")
    public List<ServiceResource> services;
}
