// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class MemoryGlobalRoutingService extends AbstractComponent implements GlobalRoutingService {

    private final Map<String, Map<ZoneId, RotationStatus>> status = new HashMap<>();

    @Override
    public Map<ZoneId, RotationStatus> getHealthStatus(String rotationName) {
        if (status.isEmpty()) {
            return Map.of(ZoneId.from("prod", "us-west-1"), RotationStatus.IN);
        }
        return Collections.unmodifiableMap(status.getOrDefault(rotationName, Map.of()));
    }

    public MemoryGlobalRoutingService setStatus(String rotation, ZoneId zone, RotationStatus status) {
        this.status.putIfAbsent(rotation, new HashMap<>());
        this.status.get(rotation).put(zone, status);
        return this;
    }

}
