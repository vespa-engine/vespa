// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author andreer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceListResponseEntity {
    public final List<String> services;

    @JsonCreator
    public ServiceListResponseEntity(@JsonProperty("names") List<String> services) {
        this.services = services;
    }
}
