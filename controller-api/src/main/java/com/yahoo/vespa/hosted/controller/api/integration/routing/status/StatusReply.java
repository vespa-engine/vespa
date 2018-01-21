// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class StatusReply {

    @JsonProperty("status") public final RotationStatus status;
    @JsonProperty("lastUpdate") public final long lastUpdate;
    @JsonProperty("cause") public final String cause;
    @JsonProperty("agent") public final String agent;

    @JsonCreator
    public StatusReply(@JsonProperty("status") RotationStatus status,
                       @JsonProperty("lastUpdate") long lastUpdate,
                       @JsonProperty("cause") String cause,
                       @JsonProperty("agent") String agent) {
        this.status = status;
        this.lastUpdate = lastUpdate;
        this.cause = cause;
        this.agent = agent;
    }
    
}
