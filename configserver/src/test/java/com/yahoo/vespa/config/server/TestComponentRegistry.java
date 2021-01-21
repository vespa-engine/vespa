// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.server.tenant.MockTenantListener;
import com.yahoo.vespa.config.server.tenant.TenantListener;

/**
 * @author Ulf Lilleengen
 */
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final TenantListener tenantListener;

    private TestComponentRegistry(TenantListener tenantListener) {
        this.tenantListener = tenantListener;
    }

    public static class Builder {
        private final MockTenantListener tenantListener = new MockTenantListener();

        public TestComponentRegistry build() {
            return new TestComponentRegistry(tenantListener);
        }
    }

    @Override
    public TenantListener getTenantListener() { return tenantListener; }

}
