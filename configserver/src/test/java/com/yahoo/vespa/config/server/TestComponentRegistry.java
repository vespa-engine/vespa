// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.server.application.TenantApplicationsTest;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import com.yahoo.vespa.config.server.tenant.MockTenantListener;
import com.yahoo.vespa.config.server.tenant.TenantListener;
import com.yahoo.vespa.model.VespaModelFactory;

import java.time.Clock;
import java.util.Collections;

/**
 * @author Ulf Lilleengen
 */
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final ConfigDefinitionRepo defRepo;
    private final ReloadListener reloadListener;
    private final TenantListener tenantListener;
    private final ModelFactoryRegistry modelFactoryRegistry;
    private final Clock clock;

    private TestComponentRegistry(ModelFactoryRegistry modelFactoryRegistry,
                                  ConfigDefinitionRepo defRepo,
                                  ReloadListener reloadListener,
                                  TenantListener tenantListener,
                                  Clock clock) {
        this.reloadListener = reloadListener;
        this.tenantListener = tenantListener;
        this.defRepo = defRepo;
        this.modelFactoryRegistry = modelFactoryRegistry;
        this.clock = clock;
    }

    public static class Builder {
        private ConfigDefinitionRepo defRepo = new StaticConfigDefinitionRepo();
        private ReloadListener reloadListener = new TenantApplicationsTest.MockReloadListener();
        private final MockTenantListener tenantListener = new MockTenantListener();
        private ModelFactoryRegistry modelFactoryRegistry = new ModelFactoryRegistry(Collections.singletonList(new VespaModelFactory(new NullConfigModelRegistry())));
        private Clock clock = Clock.systemUTC();

        public Builder modelFactoryRegistry(ModelFactoryRegistry modelFactoryRegistry) {
            this.modelFactoryRegistry = modelFactoryRegistry;
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
                                             defRepo,
                                             reloadListener,
                                             tenantListener,
                                             clock);
        }
    }

    @Override
    public TenantListener getTenantListener() { return tenantListener; }
    @Override
    public ReloadListener getReloadListener() { return reloadListener; }
    @Override
    public ConfigDefinitionRepo getStaticConfigDefinitionRepo() { return defRepo; }
    @Override
    public ModelFactoryRegistry getModelFactoryRegistry() { return modelFactoryRegistry; }
    @Override
    public Clock getClock() { return clock;}

}
