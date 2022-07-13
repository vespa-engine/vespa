// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.concurrent.StripedExecutor;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.ConfigServerDB;
import com.yahoo.vespa.config.server.MockSecretStore;
import com.yahoo.vespa.config.server.ConfigActivationListener;
import com.yahoo.vespa.config.server.TestConfigDefinitionRepo;
import com.yahoo.vespa.config.server.application.TenantApplicationsTest;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionFactory;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.model.VespaModelFactory;

import java.time.Clock;
import java.util.List;

/**
 *
 * @author hmusum
 */
public class TestTenantRepository extends TenantRepository {

    public TestTenantRepository(HostRegistry hostRegistry,
                                Curator curator,
                                Metrics metrics,
                                FileDistributionFactory fileDistributionFactory,
                                FlagSource flagSource,
                                HostProvisionerProvider hostProvisionerProvider,
                                ConfigserverConfig configserverConfig,
                                Zone zone,
                                Clock clock,
                                ModelFactoryRegistry modelFactoryRegistry,
                                ConfigDefinitionRepo configDefinitionRepo,
                                ConfigActivationListener configActivationListener,
                                TenantListener tenantListener) {
        super(hostRegistry,
              curator,
              metrics,
              new StripedExecutor<>(new InThreadExecutorService()),
              new StripedExecutor<>(new InThreadExecutorService()),
              fileDistributionFactory,
              flagSource,
              new InThreadExecutorService(),
              new MockSecretStore(),
              hostProvisionerProvider,
              configserverConfig,
              new ConfigServerDB(configserverConfig),
              zone,
              clock,
              modelFactoryRegistry,
              configDefinitionRepo,
              configActivationListener,
              tenantListener,
              new ZookeeperServerConfig.Builder().myid(0).build());
    }

    public static class Builder {
        Clock clock = Clock.systemUTC();
        ConfigDefinitionRepo configDefinitionRepo = new TestConfigDefinitionRepo();
        HostRegistry hostRegistry = new HostRegistry();
        Curator curator = new MockCurator();
        Metrics metrics = Metrics.createTestMetrics();
        FileDistributionFactory fileDistributionFactory = null;
        FlagSource flagSource = new InMemoryFlagSource();
        HostProvisionerProvider hostProvisionerProvider = HostProvisionerProvider.empty();
        ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(List.of(VespaModelFactory.createTestFactory()));
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder().build();
        ConfigActivationListener configActivationListener = new TenantApplicationsTest.MockConfigActivationListener();
        TenantListener tenantListener = new MockTenantListener();
        Zone zone = Zone.defaultZone();

        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder withFlagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
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

        public Builder withModelFactoryRegistry(ModelFactoryRegistry modelFactoryRegistry) {
            this.modelFactoryRegistry = modelFactoryRegistry;
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

        public Builder withConfigserverConfig(ConfigserverConfig configserverConfig) {
            this.configserverConfig = configserverConfig;
            return this;
        }

        public Builder withConfigActivationListener(ConfigActivationListener configActivationListener) {
            this.configActivationListener = configActivationListener;
            return this;
        }

        public Builder withTenantListener(TenantListener tenantListener) {
            this.tenantListener = tenantListener;
            return this;
        }

        public Builder withZone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public TenantRepository build() {
            if (fileDistributionFactory == null)
                fileDistributionFactory = new FileDistributionFactory(configserverConfig);
            return new TestTenantRepository(hostRegistry,
                                            curator,
                                            metrics,
                                            fileDistributionFactory,
                                            flagSource,
                                            hostProvisionerProvider,
                                            configserverConfig,
                                            zone,
                                            clock,
                                            modelFactoryRegistry,
                                            configDefinitionRepo,
                                            configActivationListener,
                                            tenantListener);
        }

    }

}
