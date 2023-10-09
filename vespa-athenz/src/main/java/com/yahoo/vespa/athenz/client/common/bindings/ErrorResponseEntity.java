// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.common.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponseEntity {

    public final int code;
    public final String description;

    @JsonCreator
    public ErrorResponseEntity(@JsonProperty("code") int code,
                               @JsonProperty("message") String description) {
        this.code = code;
        this.description = description;
    }
}
