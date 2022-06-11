// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.commons;

import ai.vespa.hosted.cd.Deployment;
import ai.vespa.hosted.cd.Endpoint;
import ai.vespa.hosted.cd.EndpointAuthenticator;

import java.net.URI;
import java.time.Instant;
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
    private final String platform;
    private final long revision;
    private final Instant deployedAt;

    /** Creates a representation of the given deployment endpoints, using the authenticator for data plane access. */
    public HttpDeployment(String platform, long revision, Instant deployedAt,
                          Map<String, URI> endpoints, EndpointAuthenticator authenticator) {
        this.platform = platform;
        this.revision = revision;
        this.deployedAt = deployedAt;
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

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public Instant deployedAt() {
        return deployedAt;
    }

}
