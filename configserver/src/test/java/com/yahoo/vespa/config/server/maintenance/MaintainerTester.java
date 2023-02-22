// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockLogRetriever;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.deploy.DeployTester;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.List;

class MaintainerTester {

    private final Curator curator;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    MaintainerTester(Clock clock, TemporaryFolder temporaryFolder) throws IOException {
        this.curator = new MockCurator();
        InMemoryProvisioner hostProvisioner = new InMemoryProvisioner(9, false);
        Provisioner provisioner = new MockProvisioner().hostProvisioner(hostProvisioner);
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .hostedVespa(true)
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        tenantRepository = new TestTenantRepository.Builder()
                .withClock(clock)
                .withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(provisioner, true))
                .withConfigserverConfig(configserverConfig)
                .withModelFactoryRegistry(new ModelFactoryRegistry(List.of(new DeployTester.CountingModelFactory(clock))))
                .build();
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(provisioner)
                .withOrchestrator(new OrchestratorMock())
                .withLogRetriever(new MockLogRetriever())
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .build();
    }

    void deployApp(File applicationPath, PrepareParams.Builder prepareParams) {
        applicationRepository.deploy(applicationPath, prepareParams.ignoreValidationErrors(true).build());
    }

    Curator curator() { return curator; }

    TenantRepository tenantRepository() { return tenantRepository; }

    ApplicationRepository applicationRepository() { return applicationRepository;}

}
