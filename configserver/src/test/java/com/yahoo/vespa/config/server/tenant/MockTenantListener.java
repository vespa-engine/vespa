// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;

/**
 * @author Ulf Lilleengen
 */
public class MockTenantListener implements TenantListener {

    TenantName tenantCreatedName;
    TenantName tenantDeletedName;
    boolean tenantsLoaded;

    @Override
    public void onTenantCreate(Tenant tenant) { this.tenantCreatedName = tenant.getName(); }

    @Override
    public void onTenantDelete(TenantName tenantName) {
        this.tenantDeletedName = tenantName;
    }

    @Override
    public void onTenantsLoaded() {
        tenantsLoaded = true;
    }

}
