// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public ConfigServerInfo(URI loadBalancerEndpoint, List<String> configServerHostNames,
                            AthenzIdentity configServerAthenzIdentity) {
        this.loadBalancerEndpoint = loadBalancerEndpoint;
        this.configServerIdentity = configServerAthenzIdentity;
        this.configServerHostnameToUriMapper = hostname -> URI.create("https://" + hostname + ":4443");
        this.configServerURIs = configServerHostNames.stream()
                .map(configServerHostnameToUriMapper)
                .collect(Collectors.toUnmodifiableList());
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
