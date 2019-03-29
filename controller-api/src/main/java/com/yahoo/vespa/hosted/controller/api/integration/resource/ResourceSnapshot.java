// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import java.time.Instant;

/**
 * @author olaa
 */
public class ResourceSnapshot {

    private final ResourceAllocation resourceAllocation;
    private final Instant timestamp;

    public ResourceSnapshot(ResourceAllocation resourceAllocation, Instant timestamp) {
        this.resourceAllocation = resourceAllocation;
        this.timestamp = timestamp;
    }

    public ResourceAllocation getResourceAllocation() {
        return resourceAllocation;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

}
