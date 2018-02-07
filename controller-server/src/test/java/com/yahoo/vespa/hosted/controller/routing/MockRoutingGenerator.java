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
        // TODO: TLS: Update to HTTPS when ready.
        endpoints.add(new RoutingEndpoint("http://old-endpoint.vespa.yahooapis.com:4080", false));
        endpoints.add(new RoutingEndpoint("http://qrs-endpoint.vespa.yahooapis.com:4080", "host1", false));
        endpoints.add(new RoutingEndpoint("http://feeding-endpoint.vespa.yahooapis.com:4080", "host2", false));
        endpoints.add(new RoutingEndpoint("http://global-endpoint.vespa.yahooapis.com:4080", "host1", true));
        endpoints.add(new RoutingEndpoint("http://alias-endpoint.vespa.yahooapis.com:4080", "host1", true));
        return endpoints;
    }

}
