// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class MemoryGlobalRoutingService implements GlobalRoutingService {

    @Override
    public Map<String, RotationStatus> getHealthStatus(String rotationName) {
        HashMap<String, RotationStatus> map = new HashMap<>();
        map.put("prod.us-west-1", RotationStatus.IN);
        return Collections.unmodifiableMap(map);
    }

}
