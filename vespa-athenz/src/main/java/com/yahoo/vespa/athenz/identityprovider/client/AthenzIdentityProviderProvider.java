package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.jdisc.Metric;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author olaa
 */
public class AthenzIdentityProviderProvider implements Provider<AthenzIdentityProvider> {

    private final Path NODE_ADMIN_MANAGED_IDENTITY_DOCUMENT = Paths.get("/var/lib/sia/vespa-tenant-identity-document.json");
    private final AthenzIdentityProvider athenzIdentityProvider;

    @Inject
    public AthenzIdentityProviderProvider(IdentityConfig config, Metric metric) {
        if (Files.exists(NODE_ADMIN_MANAGED_IDENTITY_DOCUMENT))
            athenzIdentityProvider = new AthenzIdentityProviderImpl(config, metric);
        else
            athenzIdentityProvider = new LegacyAthenzIdentityProviderImpl(config, metric);
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
