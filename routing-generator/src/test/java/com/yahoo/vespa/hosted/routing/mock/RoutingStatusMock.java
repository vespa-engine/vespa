// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing.mock;

import com.yahoo.vespa.hosted.routing.status.RoutingStatus;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mortent
 */
public class RoutingStatusMock implements RoutingStatus {

    private final Set<String> outOfRotation = new HashSet<>();

    @Override
    public boolean isActive(String upstreamName) {
        return !outOfRotation.contains(upstreamName);
    }

    public RoutingStatusMock setStatus(String upstreamName, boolean active) {
        if (active) {
            outOfRotation.remove(upstreamName);
        } else {
            outOfRotation.add(upstreamName);
        }
        return this;
    }
}
