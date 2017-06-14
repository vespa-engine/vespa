// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class ListTenantsResponseTest extends TenantTest {

    private final TenantName a = TenantName.from("a");
    private final TenantName b = TenantName.from("b");
    private final TenantName c = TenantName.from("c");

    @Test
    public void testJsonSerialization() throws Exception {
        final Collection<TenantName> tenantNames = Arrays.asList(a, b, c);
        final ListTenantsResponse response = new ListTenantsResponse(tenantNames);
        assertResponseEquals(response, "{\"tenants\":[\"a\",\"b\",\"c\"]}");
    }

    @Test
    public void testJsonSerializationNoTenants() throws Exception {
        final Collection<TenantName> tenantNames = Collections.emptyList();
        final ListTenantsResponse response = new ListTenantsResponse(tenantNames);
        assertResponseEquals(response, "{\"tenants\":[]}");
    }
}
