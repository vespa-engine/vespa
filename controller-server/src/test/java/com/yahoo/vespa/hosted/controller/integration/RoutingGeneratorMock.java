// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns a default set of endpoints on every query if it has no mappings, or those added by the user, otherwise.
 *
 * @author bratseth
 * @author jonmv
 */
public class RoutingGeneratorMock implements RoutingGenerator {

    private final Map<DeploymentId, List<RoutingEndpoint>> allEndpoints = new ConcurrentHashMap<>();
    private static final List<RoutingEndpoint> defaultEndpoints =
            Arrays.asList(new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", false),
                          new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false),
                          new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false),
                          new RoutingEndpoint("http://global-endpoint.vespa.yahooapis.com:4080", "host1", true),
                          new RoutingEndpoint("http://alias-endpoint.vespa.yahooapis.com:4080", "host1", true));

    @Override
    public List<RoutingEndpoint> endpoints(DeploymentId deployment) {
        return allEndpoints.isEmpty()
                ? defaultEndpoints
                : allEndpoints.getOrDefault(deployment, Collections.emptyList());
    }

    public void putEndpoints(DeploymentId deployment, List<RoutingEndpoint> endpoints) {
        allEndpoints.put(deployment, endpoints);
    }

    public void removeEndpoints(DeploymentId deployment) {
        allEndpoints.remove(deployment);
    }

}
