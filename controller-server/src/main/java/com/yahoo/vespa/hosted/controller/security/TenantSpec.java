package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * A specification of a tenant, typically to create or modify one.
 *
 * @author jonmv
 */
public abstract class TenantSpec {

    private final TenantName tenant;

    protected TenantSpec(TenantName tenant) {
        this.tenant = requireNonNull(tenant);
    }

    /** The name of the tenant. */
    public TenantName tenant() { return tenant; }

}
