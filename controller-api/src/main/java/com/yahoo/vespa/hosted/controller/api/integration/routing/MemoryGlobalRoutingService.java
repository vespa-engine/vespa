// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

/**
 * @author bratseth
 */
public class MemoryGlobalRoutingService implements GlobalRoutingService {

    @Override
    public Map<ZoneId, RotationStatus> getHealthStatus(String rotationName) {
        return Map.of(ZoneId.from("prod", "us-west-1"), RotationStatus.IN);
    }

}
