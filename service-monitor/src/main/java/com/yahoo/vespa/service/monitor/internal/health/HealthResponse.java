// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.health;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.text.JSON;

/**
 * Response entity from /state/v1/health
 *
 * @author hakon
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthResponse {
    @JsonProperty("status")
    public Status status = new Status();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        public static final String DEFAULT_STATUS = "down";

        @JsonProperty("code")
        public String code = DEFAULT_STATUS;

        @Override
        public String toString() {
            return "{ \"code\": \"" + JSON.escape(code) + "\" }";
        }
    }

    @Override
    public String toString() {
        return "{ \"status\": " + status.toString() + " }";
    }
}
