// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;

/**
 *
 * @author hmusum
 */
public class TestTenantRepository extends TenantRepository {

    public TestTenantRepository(GlobalComponentRegistry componentRegistry,
                                HostRegistry hostRegistry,
                                Curator curator,
                                Metrics metrics,
                                FileDistributionFactory fileDistributionFactory,
                                FlagSource flagSource,
                                HostProvisionerProvider hostProvisionerProvider) {
        super(componentRegistry,
              hostRegistry,
              curator,
              metrics,
              new StripedExecutor<>(new InThreadExecutorService()),
              fileDistributionFactory,
              flagSource,
              new InThreadExecutorService(),
              new MockSecretStore(),
              hostProvisionerProvider);
    }

    public static class Builder {

        GlobalComponentRegistry componentRegistry;
        HostRegistry hostRegistry = new HostRegistry();
        Curator curator = new MockCurator();
        Metrics metrics = Metrics.createTestMetrics();
        FileDistributionFactory fileDistributionFactory = null;
        FlagSource flagSource = new InMemoryFlagSource();
        HostProvisionerProvider hostProvisionerProvider = HostProvisionerProvider.empty();

        public Builder withFlagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
            return this;
        }

        public Builder withComponentRegistry(GlobalComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
            return this;
        }

        public Builder withHostRegistry(HostRegistry hostRegistry) {
            this.hostRegistry = hostRegistry;
            return this;
        }

        public Builder withCurator(Curator curator) {
            this.curator = curator;
            return this;
        }

        public Builder withMetrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder withFileDistributionFactory(FileDistributionFactory fileDistributionFactory) {
            this.fileDistributionFactory = fileDistributionFactory;
            return this;
        }

        public Builder withHostProvisionerProvider(HostProvisionerProvider hostProvisionerProvider) {
            this.hostProvisionerProvider = hostProvisionerProvider;
            return this;
        }

        public TenantRepository build() {
            if (fileDistributionFactory == null)
                fileDistributionFactory = new FileDistributionFactory(componentRegistry.getConfigserverConfig());
            return new TestTenantRepository(componentRegistry,
                                            hostRegistry,
                                            curator,
                                            metrics,
                                            fileDistributionFactory,
                                            flagSource,
                                            hostProvisionerProvider);
        }

    }

}
