// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.io.File;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;

import static com.yahoo.vespa.config.server.SuperModelRequestHandlerTest.emptyNodeFlavors;

/**
 * @author Ulf Lilleengen
 */
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final Curator curator;
    private final ConfigCurator configCurator;
    private final Metrics metrics;
    private final SessionPreparer sessionPreparer;
    private final ConfigserverConfig configserverConfig;
    private final SuperModelGenerationCounter superModelGenerationCounter;
    private final ConfigDefinitionRepo defRepo;
    private final ReloadListener reloadListener;
    private final TenantListener tenantListener;
    private final PermanentApplicationPackage permanentApplicationPackage;
    private final HostRegistries hostRegistries;
    private final FileDistributionFactory fileDistributionFactory;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final Optional<Provisioner> hostProvisioner;
    private final Zone zone;
    private final Clock clock;
    private final ConfigServerDB configServerDB;

    private TestComponentRegistry(Curator curator, ConfigCurator configCurator, Metrics metrics,
                                  ModelFactoryRegistry modelFactoryRegistry,
                                  PermanentApplicationPackage permanentApplicationPackage,
                                  FileDistributionFactory fileDistributionFactory,
                                  SuperModelGenerationCounter superModelGenerationCounter,
                                  HostRegistries hostRegistries,
                                  ConfigserverConfig configserverConfig,
                                  SessionPreparer sessionPreparer,
                                  Optional<Provisioner> hostProvisioner,
                                  ConfigDefinitionRepo defRepo,
                                  ReloadListener reloadListener,
                                  TenantListener tenantListener,
                                  Zone zone,
                                  Clock clock) {
        this.curator = curator;
        this.configCurator = configCurator;
        this.metrics = metrics;
        this.configserverConfig = configserverConfig;
        this.reloadListener = reloadListener;
        this.tenantListener = tenantListener;
        this.superModelGenerationCounter = superModelGenerationCounter;
        this.defRepo = defRepo;
        this.permanentApplicationPackage = permanentApplicationPackage;
        this.hostRegistries = hostRegistries;
        this.fileDistributionFactory = fileDistributionFactory;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.hostProvisioner = hostProvisioner;
        this.sessionPreparer = sessionPreparer;
        this.zone = zone;
        this.clock = clock;
        this.configServerDB = new ConfigServerDB(configserverConfig);
    }

    public static class Builder {

        private Curator curator = new MockCurator();
        private Optional<ConfigCurator> configCurator = Optional.empty();
        private Metrics metrics = Metrics.createTestMetrics();
        private ConfigserverConfig configserverConfig = new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .configServerDBDir(Files.createTempDir().getAbsolutePath())
                        .configDefinitionsDir(Files.createTempDir().getAbsolutePath()));
        private ConfigDefinitionRepo defRepo = new StaticConfigDefinitionRepo();
        private TenantRequestHandlerTest.MockReloadListener reloadListener = new TenantRequestHandlerTest.MockReloadListener();
        private MockTenantListener tenantListener = new MockTenantListener();
        private Optional<PermanentApplicationPackage> permanentApplicationPackage = Optional.empty();
        private HostRegistries hostRegistries = new HostRegistries();
        private Optional<FileDistributionFactory> fileDistributionFactory = Optional.empty();
        private ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry())));
        private Optional<Provisioner> hostProvisioner = Optional.empty();
        private Zone zone = Zone.defaultZone();
        private Clock clock = Clock.systemUTC();

        public Builder configServerConfig(ConfigserverConfig configserverConfig) {
            this.configserverConfig = configserverConfig;
            return this;
        }

        public Builder curator(Curator curator) {
            this.curator = curator;
            return this;
        }

        public Builder configCurator(ConfigCurator configCurator) {
            this.configCurator = Optional.ofNullable(configCurator);
            return this;
        }

        public Builder metrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder modelFactoryRegistry(ModelFactoryRegistry modelFactoryRegistry) {
            this.modelFactoryRegistry = modelFactoryRegistry;
            return this;
        }

        public Builder permanentApplicationPackage(PermanentApplicationPackage permanentApplicationPackage) {
            this.permanentApplicationPackage = Optional.ofNullable(permanentApplicationPackage);
            return this;
        }

        public Builder provisioner(Provisioner provisioner) {
            this.hostProvisioner = Optional.ofNullable(provisioner);
            return this;
        }

        public Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public TestComponentRegistry build() {
            final PermanentApplicationPackage permApp = this.permanentApplicationPackage
                    .orElse(new PermanentApplicationPackage(configserverConfig));
            FileDistributionFactory fileDistributionFactory = this.fileDistributionFactory
                    .orElse(new MockFileDistributionFactory(new File(configserverConfig.fileReferencesDir())));
            HostProvisionerProvider hostProvisionerProvider = hostProvisioner.isPresent() ?
                    HostProvisionerProvider.withProvisioner(hostProvisioner.get()) :
                    HostProvisionerProvider.empty();
            SessionPreparer sessionPreparer = new SessionPreparer(modelFactoryRegistry, fileDistributionFactory,
                                                                  hostProvisionerProvider, permApp,
                                                                  configserverConfig, defRepo, curator,
                                                                  zone);
            return new TestComponentRegistry(curator, configCurator.orElse(ConfigCurator.create(curator)),
                                             metrics, modelFactoryRegistry,
                                             permApp,
                                             fileDistributionFactory,
                                             new SuperModelGenerationCounter(curator),
                                             hostRegistries, configserverConfig, sessionPreparer,
                                             hostProvisioner, defRepo, reloadListener,
                                             tenantListener, zone, clock);
        }
    }

    @Override
    public Curator getCurator() { return curator; }
    @Override
    public ConfigCurator getConfigCurator() { return configCurator; }
    @Override
    public Metrics getMetrics() { return metrics; }
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
        return zone;
    }
    @Override
    public Clock getClock() { return clock;}
    @Override
    public ConfigServerDB getConfigServerDB() { return configServerDB;}


    public FileDistributionFactory getFileDistributionFactory() { return fileDistributionFactory; }

}
