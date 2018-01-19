// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusReply {

    public final RotationStatus status;
    public final long lastUpdate;
    public final String cause;
    public final String agent;

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
