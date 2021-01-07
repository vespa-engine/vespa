// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import java.util.stream.Stream;

public enum OrchestratorStatus {
    NO_REMARKS, ALLOWED_TO_BE_DOWN, PERMANENTLY_DOWN;

    public static OrchestratorStatus fromString(String statusString) {
        return Stream.of(OrchestratorStatus.values())
                .filter(status -> status.name().equalsIgnoreCase(statusString))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown orchestrator status: " + statusString));
    }
}
