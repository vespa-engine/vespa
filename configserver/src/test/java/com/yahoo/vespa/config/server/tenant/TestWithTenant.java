// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import org.junit.Before;

/**
 * Utility for a test using a single default tenant.
 *
 * @author lulf
 * @since 5.35
 */
public class TestWithTenant extends TestWithCurator {

    protected TenantRepository tenantRepository;
    protected Tenant tenant;

    @Before
    public void setupTenant() throws Exception {
        tenantRepository = new TenantRepository(new TestComponentRegistry.Builder().curator(curator).build());
        tenant = tenantRepository.defaultTenant();
    }

}
