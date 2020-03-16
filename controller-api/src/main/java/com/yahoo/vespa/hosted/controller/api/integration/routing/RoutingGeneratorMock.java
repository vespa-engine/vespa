// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Returns a default set of endpoints on every query if it has no mappings, or those added by the user, otherwise.
 *
 * @author bratseth
 * @author jonmv
 */
// TODO(mpolden): Remove
public class RoutingGeneratorMock implements RoutingGenerator {

    private final Map<DeploymentId, List<RoutingEndpoint>> routingTable = new ConcurrentHashMap<>();

    @Override
    public List<RoutingEndpoint> endpoints(DeploymentId deployment) {
        return routingTable.getOrDefault(deployment, List.of());
    }

    @Override
    public Map<ClusterSpec.Id, URI> clusterEndpoints(DeploymentId deployment) {
        return endpoints(deployment).stream()
                                    .limit(1)
                                    .collect(Collectors.toMap(__ -> ClusterSpec.Id.from("default"),
                                                              endpoint -> URI.create(endpoint.endpoint())));
    }

    public void putEndpoints(DeploymentId deployment, List<RoutingEndpoint> endpoints) {
        routingTable.put(deployment, endpoints);
    }

    public void removeEndpoints(DeploymentId deployment) {
        routingTable.remove(deployment);
    }

}
