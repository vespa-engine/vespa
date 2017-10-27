// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.impl.AthenzIdentityProviderImpl;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * @author mortent
 */
public class Identity extends SimpleComponent implements IdentityConfig.Producer {
    public static final String CLASS = AthenzIdentityProviderImpl.class.getName();

    private final String domain;
    private final String service;
    private final String loadBalancerAddress;

    public Identity(String domain, String service, String loadBalancerAddress) {
        super(CLASS);
        this.domain = domain;
        this.service = service;
        this.loadBalancerAddress = loadBalancerAddress;
    }

    @Override
    public void getConfig(IdentityConfig.Builder builder) {
        builder.domain(domain);
        builder.service(service);
        builder.loadBalancerAddress(loadBalancerAddress);
    }
}
