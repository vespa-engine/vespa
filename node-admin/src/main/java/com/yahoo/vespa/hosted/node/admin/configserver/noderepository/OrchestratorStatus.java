// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import java.util.stream.Stream;

public enum OrchestratorStatus {
    NO_REMARKS, ALLOWED_TO_BE_DOWN, PERMANENTLY_DOWN, UNKNOWN;

    public static OrchestratorStatus fromString(String statusString) {
        return Stream.of(values())
                .filter(status -> status.asString().equals(statusString))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public String asString() {
        return name();
    }

    public boolean isSuspended() {
        return this != NO_REMARKS;
    }
}
