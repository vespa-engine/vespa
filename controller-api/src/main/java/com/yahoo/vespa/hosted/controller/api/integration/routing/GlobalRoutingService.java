// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

/**
 * A service containing the health status of global rotations.
 *
 * @author mpolden
 */
public interface GlobalRoutingService {

    /** Returns the health status of each zone behind the given rotation name */
    Map<ZoneId, RotationStatus> getHealthStatus(String rotationName);

}
