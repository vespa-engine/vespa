// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.net.URI;
import java.util.Optional;

/**
 * @author mortent
 */
public class Identity extends SimpleComponent implements IdentityConfig.Producer {
    public static final String CLASS = AthenzIdentityProviderImpl.class.getName();

    private final String domain;
    private final String service;
    private final URI loadBalancerAddress;

    public Identity(String domain, String service, URI loadBalancerAddress) {
        super(CLASS);
        this.domain = domain;
        this.service = service;
        this.loadBalancerAddress = loadBalancerAddress;
    }

    @Override
    public void getConfig(IdentityConfig.Builder builder) {
        builder.domain(domain);
        builder.service(service);
        // Load balancer address might not have been set
        // Current interpretation of loadbalancer address is: hostname.
        // Config should be renamed or send the uri
        builder.loadBalancerAddress(
                Optional.ofNullable(loadBalancerAddress)
                .map(URI::getHost)
                .orElse("")
        );
    }
}
