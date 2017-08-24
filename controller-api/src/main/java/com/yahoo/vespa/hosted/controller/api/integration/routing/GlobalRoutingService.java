// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import java.util.Map;

/**
 * A global routing service.
 *
 * @author mpolden
 */
public interface GlobalRoutingService {

    /** Returns the health status for each endpoint behind the given rotation name */
    Map<String, RotationStatus> getHealthStatus(String rotationName);

}
