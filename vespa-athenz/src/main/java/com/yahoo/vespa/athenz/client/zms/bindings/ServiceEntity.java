// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author andreer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceEntity {
    public final String name;

    @JsonCreator
    public ServiceEntity(@JsonProperty("name") String name) {
        this.name = name;
    }

    @JsonGetter("name")
    public String name() {
        return name;
    }
}
