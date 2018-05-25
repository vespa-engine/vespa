// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.time.Clock;

class MaintainerTester {

    private final Curator curator;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;

    MaintainerTester() {
        curator = new MockCurator();
        GlobalComponentRegistry componentRegistry = new TestComponentRegistry.Builder().curator(curator).build();
        tenantRepository = new TenantRepository(componentRegistry, false);
        applicationRepository = new ApplicationRepository(tenantRepository, new SessionHandlerTest.MockProvisioner(), Clock.systemUTC());
    }

    Curator curator() { return curator; }
    TenantRepository tenantRepository() { return tenantRepository; }

    ApplicationRepository applicationRepository() { return applicationRepository;}

}
