// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.filedistribution.FileServer;
import com.yahoo.vespa.config.server.host.ConfigRequestHostLivenessTracker;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.rpc.RpcServer;
import com.yahoo.vespa.config.server.session.*;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.model.VespaModelFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class InjectedGlobalComponentRegistryTest {

    private Curator curator;
    private Metrics metrics;
    private SessionPreparer sessionPreparer;
    private ConfigserverConfig configserverConfig;
    private RpcServer rpcServer;
    private SuperModelGenerationCounter generationCounter;
    private ConfigDefinitionRepo defRepo;
    private PermanentApplicationPackage permanentApplicationPackage;
    private HostRegistries hostRegistries;
    private GlobalComponentRegistry globalComponentRegistry;
    private ModelFactoryRegistry modelFactoryRegistry;
    private Zone zone;

    @Before
    public void setupRegistry() {
        curator = new MockCurator();
        ConfigCurator configCurator = ConfigCurator.create(curator);
        metrics = Metrics.createTestMetrics();
        modelFactoryRegistry = new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry())));
        configserverConfig = new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .configServerDBDir(Files.createTempDir().getAbsolutePath())
                        .configDefinitionsDir(Files.createTempDir().getAbsolutePath()));
        sessionPreparer = new SessionTest.MockSessionPreparer();
        rpcServer = new RpcServer(configserverConfig, null, Metrics.createTestMetrics(), 
                                  new HostRegistries(), new ConfigRequestHostLivenessTracker(), new FileServer(FileDistribution.getDefaultFileDBPath()));
        generationCounter = new SuperModelGenerationCounter(curator);
        defRepo = new StaticConfigDefinitionRepo();
        permanentApplicationPackage = new PermanentApplicationPackage(configserverConfig);
        hostRegistries = new HostRegistries();
        HostProvisionerProvider hostProvisionerProvider = HostProvisionerProvider.withProvisioner(new SessionHandlerTest.MockProvisioner());
        zone = Zone.defaultZone();
        globalComponentRegistry =
                new InjectedGlobalComponentRegistry(curator, configCurator, metrics, modelFactoryRegistry, sessionPreparer, rpcServer, configserverConfig,
                                                    generationCounter, defRepo, permanentApplicationPackage, hostRegistries, hostProvisionerProvider, zone);
    }

    @Test
    public void testThatAllComponentsAreSetup() {
        assertThat(globalComponentRegistry.getModelFactoryRegistry(), is(modelFactoryRegistry));
        assertThat(globalComponentRegistry.getSessionPreparer(), is(sessionPreparer));
        assertThat(globalComponentRegistry.getMetrics(), is(metrics));
        assertThat(globalComponentRegistry.getCurator(), is(curator));
        assertThat(globalComponentRegistry.getConfigserverConfig(), is(configserverConfig));
        assertThat(globalComponentRegistry.getReloadListener().hashCode(), is(rpcServer.hashCode()));
        assertThat(globalComponentRegistry.getTenantListener().hashCode(), is(rpcServer.hashCode()));
        assertThat(globalComponentRegistry.getSuperModelGenerationCounter(), is(generationCounter));
        assertThat(globalComponentRegistry.getConfigDefinitionRepo(), is(defRepo));
        assertThat(globalComponentRegistry.getPermanentApplicationPackage(), is(permanentApplicationPackage));
        assertThat(globalComponentRegistry.getHostRegistries(), is(hostRegistries));
        assertThat(globalComponentRegistry.getZone(), is (zone));
        assertTrue(globalComponentRegistry.getHostProvisioner().isPresent());
    }

}
