// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import org.junit.Test;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

public class ListTenantsTest extends TenantTest {

    private final TenantName a = TenantName.from("a");
    private final TenantName b = TenantName.from("b");
    private final TenantName c = TenantName.from("c");

    @Test
    public void testListTenants() throws Exception {
        tenants.addTenant(a);
        tenants.addTenant(b);
        tenants.addTenant(c);

        ListTenantsHandler listTenantsHandler = new ListTenantsHandler(testExecutor(), null, tenants);
        
        ListTenantsResponse response = (ListTenantsResponse) listTenantsHandler.handleGET(HttpRequest.createTestRequest("/blabla", Method.GET));
        final Collection<TenantName> responseTenantNames = response.getTenantNames();
        assertTrue(responseTenantNames.containsAll(Arrays.asList(a, b, c)));
        assertContainsSystemTenants(responseTenantNames);
    }

    private static void assertContainsSystemTenants(final Collection<TenantName> tenantNames) {
        assertTrue(tenantNames.contains(TenantName.defaultName()));
    }

    @Test
    public void testEmptyTenants() throws Exception {
        ListTenantsHandler listTenantsHandler = new ListTenantsHandler(testExecutor(), null, tenants);
        
        ListTenantsResponse response = (ListTenantsResponse) listTenantsHandler.handleGET(HttpRequest.createTestRequest("/blabla", Method.GET));
        final Collection<TenantName> responseTenantNames = response.getTenantNames();
        assertContainsSystemTenants(responseTenantNames);
    }
}
