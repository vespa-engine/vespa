// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;

/**
 * Interface for something that listens for created and deleted tenants.
 *
 * @author Ulf Lilleengen
 */
public interface TenantListener {
    /**
     * Called whenever a new tenant is created.
     *
     * @param tenant newly created tenant.
     */
    void onTenantCreate(Tenant tenant);

    /**
     * Called whenever a tenant is deleted.
     *
     * @param tenant name of deleted tenant.
     */
    void onTenantDelete(TenantName tenant);

    /**
     * Called when all tenants have been loaded at startup.
     */
    void onTenantsLoaded();
}
