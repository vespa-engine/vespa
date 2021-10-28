// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import static java.util.Objects.requireNonNull;

/**
 * A specification of a tenant, typically to create or modify one.
 *
 * @author jonmv
 */
public abstract class TenantSpec {

    private final TenantName tenant;

    protected TenantSpec(TenantName tenant) {
        this.tenant = Tenant.requireName(requireNonNull(tenant));
    }

    /** The name of the tenant. */
    public TenantName tenant() { return tenant; }

}
