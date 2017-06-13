// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import org.junit.Before;

/**
 * Utility for a test using a single default tenant.
 *
 * @author lulf
 * @since 5.35
 */
public class TestWithTenant extends TestWithCurator {

    protected Tenants tenants;
    protected Tenant tenant;

    @Before
    public void setupTenant() throws Exception {
        final Metrics metrics = Metrics.createTestMetrics();
        tenants = new Tenants(new TestComponentRegistry.Builder().curator(curator).metrics(metrics).build(), metrics);
        tenant = tenants.defaultTenant();
    }

}
