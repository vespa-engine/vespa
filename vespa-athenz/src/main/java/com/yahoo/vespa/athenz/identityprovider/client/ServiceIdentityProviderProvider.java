package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;

import javax.inject.Inject;

/**
 * @author olaa
 */
public class ServiceIdentityProviderProvider implements Provider<ServiceIdentityProvider>  {

    private AthenzIdentityProvider athenzIdentityProvider;

    @Inject
    public ServiceIdentityProviderProvider(AthenzIdentityProvider athenzIdentityProvider) {
        this.athenzIdentityProvider = athenzIdentityProvider;
    }

    @Override
    public ServiceIdentityProvider get() {
        if (athenzIdentityProvider instanceof AthenzIdentityProviderImpl impl) return impl;
        if (athenzIdentityProvider instanceof LegacyAthenzIdentityProviderImpl legacyImpl) return legacyImpl;
        return null;
    }

    @Override
    public void deconstruct() {}

}
