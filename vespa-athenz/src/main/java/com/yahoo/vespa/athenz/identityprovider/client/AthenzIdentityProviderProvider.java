// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.jdisc.Metric;

import javax.inject.Inject;

/**
 * @author olaa
 */
public class AthenzIdentityProviderProvider implements Provider<AthenzIdentityProvider> {

    private final AthenzIdentityProvider athenzIdentityProvider;

    @Inject
    public AthenzIdentityProviderProvider(IdentityConfig config, Metric metric) {
        athenzIdentityProvider = new AthenzIdentityProviderImpl(config, metric);
    }

    @Override
    public void deconstruct() {
        athenzIdentityProvider.deconstruct();
    }

    @Override
    public AthenzIdentityProvider get() {
        return athenzIdentityProvider;
    }
}
