// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.IntRange;

import java.util.OptionalInt;

/**
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntRangeData {

    @JsonProperty("from")
    public Integer from;

    @JsonProperty("to")
    public Integer to;

    public IntRange toRange() {
        return new IntRange(from == null ? OptionalInt.empty() : OptionalInt.of(from),
                            to == null ? OptionalInt.empty() : OptionalInt.of(to));
    }

}
