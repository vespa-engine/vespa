// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.TenantHandlerProvider;
import com.yahoo.vespa.config.server.tenant.TenantListener;

/**
 * @author lulf
 * @since 5.8
 */
public class MockTenantListener implements TenantListener {
    TenantName tenantCreatedName;
    TenantHandlerProvider provider;
    TenantName tenantDeletedName;
    boolean tenantsLoaded;

    @Override
    public void onTenantCreate(TenantName tenantName, TenantHandlerProvider provider) {
        this.tenantCreatedName = tenantName;
        this.provider = provider;
    }

    @Override
    public void onTenantDelete(TenantName tenantName) {
        this.tenantDeletedName = tenantName;
    }

    @Override
    public void onTenantsLoaded() {
        tenantsLoaded = true;
    }
}
