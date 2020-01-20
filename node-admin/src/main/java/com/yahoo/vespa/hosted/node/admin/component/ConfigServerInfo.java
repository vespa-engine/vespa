// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.vespa.athenz.api.AthenzIdentity;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Information necessary to e.g. establish communication with the config servers
 *
 * @author hakon
 */
public class ConfigServerInfo {
    private final URI loadBalancerEndpoint;
    private final AthenzIdentity configServerIdentity;
    private final Function<String, URI> configServerHostnameToUriMapper;
    private final List<URI> configServerURIs;

    public ConfigServerInfo(String loadBalancerHostName, List<String> configServerHostNames,
                            String scheme, int port, AthenzIdentity configServerAthenzIdentity) {
        this.loadBalancerEndpoint = createLoadBalancerEndpoint(loadBalancerHostName, scheme, port);
        this.configServerIdentity = configServerAthenzIdentity;
        this.configServerHostnameToUriMapper = hostname -> URI.create(scheme + "://" + hostname + ":" + port);
        this.configServerURIs = configServerHostNames.stream()
                .map(configServerHostnameToUriMapper)
                .collect(Collectors.toUnmodifiableList());
    }

    private static URI createLoadBalancerEndpoint(String loadBalancerHost, String scheme, int port) {
        return URI.create(scheme + "://" + loadBalancerHost + ":" + port);
    }

    public List<URI> getConfigServerUris() {
        return configServerURIs;
    }

    public URI getConfigServerUri(String hostname) {
        return configServerHostnameToUriMapper.apply(hostname);
    }

    public URI getLoadBalancerEndpoint() {
        return loadBalancerEndpoint;
    }

    public AthenzIdentity getConfigServerIdentity() {
        return configServerIdentity;
    }
}
