// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.GlobalComponentRegistry;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.RemoteSessionRepo;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

import java.util.*;

/**
 * Test utility for creating tenantRepository used for testing and setup wiring of tenant stuff.
 *
 * @author Ulf Lilleengen
 */
public class TestTenantBuilder {

    private GlobalComponentRegistry componentRegistry;
    private Map<TenantName, TenantBuilder> tenantMap = new HashMap<>();

    public TestTenantBuilder() {
        componentRegistry = new TestComponentRegistry.Builder().build();
    }

    public TenantBuilder createTenant(TenantName tenantName) {
        MemoryTenantApplications applicationRepo = new MemoryTenantApplications();
        TenantBuilder builder = TenantBuilder.create(componentRegistry, tenantName)
                .withSessionFactory(new SessionCreateHandlerTest.MockSessionFactory())
                .withLocalSessionRepo(new LocalSessionRepo(componentRegistry.getClock()))
                .withRemoteSessionRepo(new RemoteSessionRepo(tenantName))
                .withApplicationRepo(applicationRepo);
        tenantMap.put(tenantName, builder);
        return builder;
    }

    public Map<TenantName, TenantBuilder> tenants() {
        return Collections.unmodifiableMap(tenantMap);
    }

    public TenantRepository createTenants() {
        Collection<Tenant> tenantList = Collections2.transform(tenantMap.values(), new Function<TenantBuilder, Tenant>() {
            @Override
            public Tenant apply(TenantBuilder builder) {
                try {
                    return builder.build();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to build tenant", e);
                }
            }
        });
        return new TenantRepository(componentRegistry, tenantList);
    }
}
