// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.restapi.wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchOperationResult {
    private static final String FAILURE_REASON = "failure-reason";

    private final Optional<String> failureReason;
    // private final List<String> suppressedFailures;

    public static BatchOperationResult successResult() {
        return new BatchOperationResult(null);
    }

    @JsonCreator
    public BatchOperationResult(
            @JsonProperty(FAILURE_REASON) String failureReason) {
        this.failureReason = Optional.ofNullable(failureReason);
    }

    @JsonProperty(FAILURE_REASON)
    public Optional<String> getFailureReason() {
        return failureReason;
    }

    public boolean success() {
        return !failureReason.isPresent();
    }
}
