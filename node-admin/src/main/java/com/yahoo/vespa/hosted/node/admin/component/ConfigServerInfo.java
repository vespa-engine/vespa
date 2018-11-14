// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Information necessary to e.g. establish communication with the config servers
 *
 * @author hakon
 */
public class ConfigServerInfo {
    private final URI loadBalancerEndpoint;
    private final Map<String, URI> configServerURIs;
    private final AthenzService configServerIdentity;

    public ConfigServerInfo(String loadBalancerHostName, List<String> configServerHostNames,
                            String scheme, int port, AthenzService configServerAthenzIdentity) {
        this.configServerURIs = createConfigServerUris(scheme, configServerHostNames, port);
        this.loadBalancerEndpoint = createLoadBalancerEndpoint(loadBalancerHostName, scheme, port);
        this.configServerIdentity = configServerAthenzIdentity;
    }

    private static URI createLoadBalancerEndpoint(String loadBalancerHost, String scheme, int port) {
        return URI.create(scheme + "://" + loadBalancerHost + ":" + port);
    }

    public List<URI> getConfigServerUris() {
        return new ArrayList<>(configServerURIs.values());
    }

    public URI getConfigServerUri(String hostname) {
        URI uri = configServerURIs.get(hostname);
        if (uri == null) {
            throw new IllegalArgumentException("There is no config server '" + hostname + "'");
        }

        return uri;
    }

    public URI getLoadBalancerEndpoint() {
        return loadBalancerEndpoint;
    }

    public AthenzService getConfigServerIdentity() {
        return configServerIdentity;
    }

    private static Map<String, URI> createConfigServerUris(
            String scheme,
            List<String> configServerHosts,
            int port) {
        return configServerHosts.stream().collect(toMap(
                Function.identity(),
                hostname -> URI.create(scheme + "://" + hostname + ":" + port)));
    }

}
