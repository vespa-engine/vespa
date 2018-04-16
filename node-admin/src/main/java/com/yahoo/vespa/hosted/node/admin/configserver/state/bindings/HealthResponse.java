// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.state.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from /state/v1/health
 *
 * @author hakon
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthResponse {
    @JsonProperty("status")
    public Status status = new Status();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @JsonProperty("code")
        public String code = "down";

        @Override
        public String toString() {
            return "Status{" +
                    "code='" + code + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "HealthResponse{" +
                "status=" + status +
                '}';
    }
}
