package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.TenantName;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Data that relates identities to permissions to a tenant.
 *
 * @author jonmv
 */
public abstract class TenantPermit {

    private final TenantName tenant;
    private final Principal user;

    protected TenantPermit(TenantName tenant, Principal user) {
        this.user = requireNonNull(user);
        this.tenant = requireNonNull(tenant);
    }

    /** The tenant this permit concerns. */
    public TenantName tenant() { return tenant; }

    /** The user handling this permit. */
    public Principal user() { return user; }

}
