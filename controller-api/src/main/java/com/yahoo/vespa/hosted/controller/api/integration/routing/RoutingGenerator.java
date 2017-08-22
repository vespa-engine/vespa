// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.util.List;

/**
 * @author bratseth
 * @author smorgrav
 */
public interface RoutingGenerator {

    /**
     * @param deploymentId Specifying an application in a zone
     * @return List of endpoints for that deploymentId
     */
    List<RoutingEndpoint> endpoints(DeploymentId deploymentId);
}
