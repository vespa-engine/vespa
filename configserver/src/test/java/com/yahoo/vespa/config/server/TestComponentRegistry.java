// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.PermanentApplicationPackage;
import com.yahoo.vespa.config.server.host.HostRegistries;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.FileDistributionFactory;
import com.yahoo.vespa.config.server.session.MockFileDistributionFactory;
import com.yahoo.vespa.config.server.session.SessionPreparer;
import com.yahoo.vespa.config.server.tenant.MockTenantListener;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.config.server.tenant.TenantRequestHandlerTest;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.config.server.zookeeper.ConfigCurator;
import com.yahoo.vespa.model.VespaModelFactory;

import java.util.Collections;
import java.util.Optional;

/**
 * @author lulf
 * @since 5.1
 */
// TODO Use a Builder to avoid so many constructors
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Metrics metrics;
    private final ConfigServerDB serverDB;
    private final SessionPreparer sessionPreparer;
    private final ConfigserverConfig configserverConfig;
    private final SuperModelGenerationCounter superModelGenerationCounter;
    private final ConfigDefinitionRepo defRepo;
    final TenantRequestHandlerTest.MockReloadListener reloadListener;
    final MockTenantListener tenantListener;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final HostRegistries hostRegistries;
    private final FileDistributionFactory fileDistributionFactory;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final Optional<Provisioner> hostProvisioner;

    public TestComponentRegistry() { this(new MockCurator()); }

    public TestComponentRegistry(Curator curator) {
       this(curator, ConfigCurator.create(curator), new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry()))));
    }

    public TestComponentRegistry(Curator curator, ConfigCurator configCurator, FileDistributionFactory fileDistributionFactory) {
       this(curator, configCurator, new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry()))), fileDistributionFactory);
    }

    public TestComponentRegistry(Curator curator, ModelFactoryRegistry modelFactoryRegistry) {
        this(curator, ConfigCurator.create(curator), modelFactoryRegistry, Optional.empty());
    }

    public TestComponentRegistry(Curator curator, ConfigCurator configCurator, ModelFactoryRegistry modelFactoryRegistry) {
        this(curator, configCurator, modelFactoryRegistry, Optional.empty());
    }

    public TestComponentRegistry(Curator curator, ConfigCurator configCurator, ModelFactoryRegistry modelFactoryRegistry, FileDistributionFactory fileDistributionFactory) {
        this(curator, configCurator, modelFactoryRegistry, Optional.empty(), fileDistributionFactory);
    }

    public TestComponentRegistry(Curator curator, ModelFactoryRegistry modelFactoryRegistry, Optional<PermanentApplicationPackage> permanentApplicationPackage) {
        this(curator, ConfigCurator.create(curator), modelFactoryRegistry, permanentApplicationPackage, new MockFileDistributionFactory());
    }

    public TestComponentRegistry(Curator curator, ConfigCurator configCurator, ModelFactoryRegistry modelFactoryRegistry, Optional<PermanentApplicationPackage> permanentApplicationPackage) {
        this(curator, configCurator, modelFactoryRegistry, permanentApplicationPackage, new MockFileDistributionFactory());
    }

    public TestComponentRegistry(Curator curator, ConfigCurator configCurator, ModelFactoryRegistry modelFactoryRegistry, Optional<PermanentApplicationPackage> permanentApplicationPackage, FileDistributionFactory fileDistributionFactory) {
        this.curator = curator;
        this.configCurator = configCurator;
        metrics = Metrics.createTestMetrics();
        configserverConfig = new ConfigserverConfig(new ConfigserverConfig.Builder().configServerDBDir(Files.createTempDir().getAbsolutePath()));
        serverDB = new ConfigServerDB(configserverConfig);
        reloadListener = new TenantRequestHandlerTest.MockReloadListener();
        tenantListener = new MockTenantListener();
        this.superModelGenerationCounter = new SuperModelGenerationCounter(curator);
        this.defRepo = new StaticConfigDefinitionRepo();
        this.permanentApplicationPackage = permanentApplicationPackage.orElse(new PermanentApplicationPackage(configserverConfig));
        this.hostRegistries = new HostRegistries();
        this.fileDistributionFactory = fileDistributionFactory;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.hostProvisioner = Optional.empty();
        sessionPreparer = new SessionPreparer(modelFactoryRegistry, fileDistributionFactory, HostProvisionerProvider.empty(), this.permanentApplicationPackage, configserverConfig, defRepo, curator, new Zone(configserverConfig));
    }

    @Override
    public Curator getCurator() { return curator; }
    @Override
    public ConfigCurator getConfigCurator() { return configCurator; }
    @Override
    public Metrics getMetrics() { return metrics; }
    @Override
    public ConfigServerDB getServerDB() { return serverDB; }
    @Override
    public SessionPreparer getSessionPreparer() { return sessionPreparer; }
    @Override
    public ConfigserverConfig getConfigserverConfig() { return configserverConfig; }
    @Override
    public TenantListener getTenantListener() { return tenantListener; }
    @Override
    public ReloadListener getReloadListener() { return reloadListener; }
    @Override
    public SuperModelGenerationCounter getSuperModelGenerationCounter() { return superModelGenerationCounter; }
    @Override
    public ConfigDefinitionRepo getConfigDefinitionRepo() { return defRepo; }
    @Override
    public PermanentApplicationPackage getPermanentApplicationPackage() { return permanentApplicationPackage; }
    @Override
    public HostRegistries getHostRegistries() { return hostRegistries;}
    @Override
    public ModelFactoryRegistry getModelFactoryRegistry() { return modelFactoryRegistry; }

    @Override
    public Optional<Provisioner> getHostProvisioner() {
        return hostProvisioner;
    }

    @Override
    public Zone getZone() {
        return Zone.defaultZone();
    }

    public FileDistributionFactory getFileDistributionFactory() { return fileDistributionFactory; }
}
