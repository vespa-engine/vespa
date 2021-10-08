// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The response returned by the cluster controller's set-node-state APIs.
 *
 * @author hakonhall
 */
public class ClusterControllerStateResponse {

    @JsonProperty("wasModified")
    public final boolean wasModified;

    @JsonProperty("reason")
    public final String reason;

    @JsonCreator
    public ClusterControllerStateResponse(@JsonProperty("wasModified") boolean wasModified,
                                          @JsonProperty("reason") String reason) {
        this.wasModified = wasModified;
        this.reason = reason;
    }

}
