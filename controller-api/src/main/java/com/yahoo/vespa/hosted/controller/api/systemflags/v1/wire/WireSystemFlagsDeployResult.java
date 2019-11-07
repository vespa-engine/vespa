// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.flags.json.wire.WireFlagData;

import java.util.List;

/**
 *
 *
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WireSystemFlagsDeployResult {
    @JsonProperty("changes") public List<WireFlagDataChange> changes;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WireFlagDataChange {
        @JsonProperty("flag-id") public String flagId;
        @JsonProperty("targets") public List<String> targets;
        @JsonProperty("operation") public String operation;
        @JsonProperty("data") public WireFlagData data;
        @JsonProperty("previous-data") public WireFlagData previousData;
    }
}


