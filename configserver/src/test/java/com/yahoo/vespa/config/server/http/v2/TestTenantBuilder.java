// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.RemoteSessionRepo;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.Tenants;

import java.util.*;

/**
 * Test utility for creating tenants used for testing and setup wiring of tenant stuff.
 *
 * @author lulf
 * @since 5.1
 */
public class TestTenantBuilder {

    private GlobalComponentRegistry componentRegistry;
    private Map<TenantName, TenantBuilder> tenantMap = new HashMap<>();

    public TestTenantBuilder() {
        componentRegistry = new TestComponentRegistry.Builder().build();
    }

    public TenantBuilder createTenant(TenantName tenantName) {
        MemoryTenantApplications applicationRepo = new MemoryTenantApplications();
        TenantBuilder builder = TenantBuilder.create(componentRegistry, tenantName, Path.createRoot().append(tenantName.value()))
                .withSessionFactory(new SessionCreateHandlerTest.MockSessionFactory())
                .withLocalSessionRepo(new LocalSessionRepo(applicationRepo))
                .withRemoteSessionRepo(new RemoteSessionRepo())
                .withApplicationRepo(applicationRepo);
        tenantMap.put(tenantName, builder);
        return builder;
    }

    public Map<TenantName, TenantBuilder> tenants() {
        return Collections.unmodifiableMap(tenantMap);
    }

    public Tenants createTenants() {
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
        return new Tenants(componentRegistry, Metrics.createTestMetrics(), tenantList);
    }
}
