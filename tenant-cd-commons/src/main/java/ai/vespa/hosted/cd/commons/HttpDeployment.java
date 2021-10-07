// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.commons;

import ai.vespa.hosted.cd.Deployment;
import ai.vespa.hosted.cd.Endpoint;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * A remote deployment of a Vespa application, reachable over HTTP. Contains {@link HttpEndpoint}s.
 *
 * @author jonmv
 */
public class HttpDeployment implements Deployment {

    private final Map<String, Endpoint> endpoints;

    /** Creates a representation of the given deployment endpoints, using the authenticator for data plane access. */
    public HttpDeployment(Map<String, URI> endpoints, EndpointAuthenticator authenticator) {
        this.endpoints = endpoints.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                      entry -> new HttpEndpoint(entry.getValue(), authenticator)));
    }

    @Override
    public Endpoint endpoint(String id) {
        if ( ! endpoints.containsKey(id))
            throw new NoSuchElementException("No cluster with id '" + id + "'");

        return endpoints.get(id);
    }

}
