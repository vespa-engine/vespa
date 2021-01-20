// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.application.TenantApplicationsTest;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.tenant.MockTenantListener;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.model.VespaModelFactory;

import java.nio.file.Files;
import java.time.Clock;
import java.util.Collections;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author Ulf Lilleengen
 */
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final ConfigserverConfig configserverConfig;
    private final ConfigDefinitionRepo defRepo;
    private final ReloadListener reloadListener;
    private final TenantListener tenantListener;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final Zone zone;
    private final Clock clock;
    private final ConfigServerDB configServerDB;

    private TestComponentRegistry(ModelFactoryRegistry modelFactoryRegistry,
                                  ConfigserverConfig configserverConfig,
                                  ConfigDefinitionRepo defRepo,
                                  ReloadListener reloadListener,
                                  TenantListener tenantListener,
                                  Zone zone,
                                  Clock clock) {
        this.configserverConfig = configserverConfig;
        this.reloadListener = reloadListener;
        this.tenantListener = tenantListener;
        this.defRepo = defRepo;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.zone = zone;
        this.clock = clock;
        this.configServerDB = new ConfigServerDB(configserverConfig);
    }

    public static class Builder {
        private ConfigserverConfig configserverConfig = new ConfigserverConfig(
                new ConfigserverConfig.Builder()
                        .configServerDBDir(uncheck(() -> Files.createTempDirectory("serverdb")).toString())
                        .configDefinitionsDir(uncheck(() -> Files.createTempDirectory("configdefinitions")).toString())
                        .sessionLifetime(5));
        private ConfigDefinitionRepo defRepo = new StaticConfigDefinitionRepo();
        private ReloadListener reloadListener = new TenantApplicationsTest.MockReloadListener();
        private final MockTenantListener tenantListener = new MockTenantListener();
        private ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry())));
        private Zone zone = Zone.defaultZone();
        private Clock clock = Clock.systemUTC();

        public Builder configServerConfig(ConfigserverConfig configserverConfig) {
            this.configserverConfig = configserverConfig;
            return this;
        }

        public Builder modelFactoryRegistry(ModelFactoryRegistry modelFactoryRegistry) {
            this.modelFactoryRegistry = modelFactoryRegistry;
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

        public Builder reloadListener(ReloadListener reloadListener) {
            this.reloadListener = reloadListener;
            return this;
        }

        public Builder configDefinitionRepo(ConfigDefinitionRepo configDefinitionRepo) {
            this.defRepo = configDefinitionRepo;
            return this;
        }

        public TestComponentRegistry build() {
            return new TestComponentRegistry(modelFactoryRegistry,
                                             configserverConfig,
                                             defRepo,
                                             reloadListener,
                                             tenantListener,
                                             zone,
                                             clock);
        }
    }

    @Override
    public ConfigserverConfig getConfigserverConfig() { return configserverConfig; }
    @Override
    public TenantListener getTenantListener() { return tenantListener; }
    @Override
    public ReloadListener getReloadListener() { return reloadListener; }
    @Override
    public ConfigDefinitionRepo getStaticConfigDefinitionRepo() { return defRepo; }
    @Override
    public ModelFactoryRegistry getModelFactoryRegistry() { return modelFactoryRegistry; }
    @Override
    public Zone getZone() {
        return zone;
    }
    @Override
    public Clock getClock() { return clock;}
    @Override
    public ConfigServerDB getConfigServerDB() { return configServerDB;}

}
