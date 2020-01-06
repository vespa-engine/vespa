// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error response from cluster controller.
 *
 * @author hakonhall
 */
public class ClusterControllerStateErrorResponse {
    @JsonProperty("message")
    public final String message;

    @JsonCreator
    public ClusterControllerStateErrorResponse(@JsonProperty("message") String message) {
        this.message = message;
    }
}
