// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * @author bratseth
 * @author smorgrav
 */
// TODO(mpolden): Remove
public interface RoutingGenerator {

    /**
     * @param deploymentId Specifying an application in a zone
     * @return List of endpoints for that deploymentId
     */
    List<RoutingEndpoint> endpoints(DeploymentId deploymentId);

    /** Returns the endpoints of each cluster in the given deployment — nothing global. */
    Map<ClusterSpec.Id, URI> clusterEndpoints(DeploymentId deploymentId);

}
