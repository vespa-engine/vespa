// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.TenantHandlerProvider;

/**
 * Interface for something that listens for created and deleted tenants.
 *
 * @author lulf
 * @since 5.8
 */
public interface TenantListener {
    /**
     * Called whenever a new tenant is created.
     *
     * @param tenant name of newly created tenant.
     * @param provider provider of request and reload handlers for new tenant.
     */
    public void onTenantCreate(TenantName tenant, TenantHandlerProvider provider);

    /**
     * Called whenever a tenant is deleted.
     *
     * @param tenant name of deleted tenant.
     */
    public void onTenantDelete(TenantName tenant);

    /**
     * Called when all tenants have been loaded at startup.
     */
    void onTenantsLoaded();
}
