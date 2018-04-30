// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.google.common.testing.EqualsTester;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.3
 */
public class TenantTest extends TestWithCurator {
    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();

    private Tenant t1;
    private Tenant t2;
    private Tenant t3;
    private Tenant t4;

    @Before
    public void setupTenant() {
        t1 = createTenant("foo");
        t2 = createTenant("foo");
        t3 = createTenant("bar");
        t4 = createTenant("baz");
    }

    private Tenant createTenant(String name) {
        TenantRepository tenantRepository = new TenantRepository(componentRegistry, false);
        TenantName tenantName = TenantName.from(name);
        TenantBuilder tenantBuilder = TenantBuilder.create(componentRegistry, tenantName)
                .withApplicationRepo(new MemoryTenantApplications());
        tenantRepository.addTenant(tenantBuilder);
        return tenantRepository.getTenant(tenantName);
    }

    @Test
    public void equals() {
        new EqualsTester()
                .addEqualityGroup(t1, t2)
                .addEqualityGroup(t3)
                .addEqualityGroup(t4)
                .testEquals();
    }

    @Test
    public void hashcode() {
        assertThat(t1.hashCode(), is(t2.hashCode()));
        assertThat(t1.hashCode(), is(not(t3.hashCode())));
        assertThat(t1.hashCode(), is(not(t4.hashCode())));
    }

    @Test
    public void close() {
        MemoryTenantApplications repo = (MemoryTenantApplications) t1.getApplicationRepo();
        assertTrue(repo.isOpen());
        t1.close();
        assertFalse(repo.isOpen());
    }
}
