// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessResponseEntity {
    public final boolean granted;

    @JsonCreator
    public AccessResponseEntity(@JsonProperty("granted") boolean granted) {
        this.granted = granted;
    }
}
