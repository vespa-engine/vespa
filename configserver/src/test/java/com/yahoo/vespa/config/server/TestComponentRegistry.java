// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.vespa.config.server.application.TenantApplicationsTest;
import com.yahoo.vespa.config.server.tenant.MockTenantListener;
import com.yahoo.vespa.config.server.tenant.TenantListener;

/**
 * @author Ulf Lilleengen
 */
public class TestComponentRegistry implements GlobalComponentRegistry {

    private final ReloadListener reloadListener;
    private final TenantListener tenantListener;

    private TestComponentRegistry(ReloadListener reloadListener, TenantListener tenantListener) {
        this.reloadListener = reloadListener;
        this.tenantListener = tenantListener;
    }

    public static class Builder {
        private ReloadListener reloadListener = new TenantApplicationsTest.MockReloadListener();
        private final MockTenantListener tenantListener = new MockTenantListener();

        public Builder reloadListener(ReloadListener reloadListener) {
            this.reloadListener = reloadListener;
            return this;
        }

        public TestComponentRegistry build() {
            return new TestComponentRegistry(reloadListener, tenantListener);
        }
    }

    @Override
    public TenantListener getTenantListener() { return tenantListener; }
    @Override
    public ReloadListener getReloadListener() { return reloadListener; }

}
