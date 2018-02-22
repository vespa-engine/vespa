// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.HostName;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * @author mortent
 */
public class IdentityProvider extends SimpleComponent implements IdentityConfig.Producer {
    public static final String CLASS = "com.yahoo.vespa.athenz.identityprovider.AthenzIdentityProviderImpl";
    public static final String BUNDLE = "vespa-athenz";

    private final AthenzDomain domain;
    private final AthenzService service;
    private final HostName loadBalancerName;

    public IdentityProvider(AthenzDomain domain, AthenzService service, HostName loadBalancerName) {
        super(new ComponentModel(BundleInstantiationSpecification.getFromStrings(CLASS, CLASS, BUNDLE)));
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
