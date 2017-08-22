// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class MockRoutingGenerator implements RoutingGenerator {

    @Override
    public List<RoutingEndpoint> endpoints(DeploymentId deployment) {
        List<RoutingEndpoint> endpoints = new ArrayList<>();
        endpoints.add(new RoutingEndpoint("qrs-endpoint", false));
        endpoints.add(new RoutingEndpoint("feeding-endpoint", false));
        endpoints.add(new RoutingEndpoint("global-endpoint", true));
        endpoints.add(new RoutingEndpoint("alias-endpoint", true));
        return endpoints;
    }

}
