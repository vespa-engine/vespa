// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.HostName;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * @author mortent
 */
public class IdentityProvider extends SimpleComponent implements IdentityConfig.Producer {
    public static final String CLASS = AthenzIdentityProviderImpl.class.getName();

    private final AthenzDomain domain;
    private final AthenzService service;
    private final HostName loadBalancerName;

    public IdentityProvider(AthenzDomain domain, AthenzService service, HostName loadBalancerName) {
        super(CLASS);
        this.domain = domain;
        this.service = service;
        this.loadBalancerName = loadBalancerName;
    }

    @Override
    public void getConfig(IdentityConfig.Builder builder) {
        builder.domain(domain.value());
        builder.service(service.value());
        // Current interpretation of loadbalancer address is: hostname.
        // Config should be renamed or send the uri
        builder.loadBalancerAddress(loadBalancerName.value());
    }
}
