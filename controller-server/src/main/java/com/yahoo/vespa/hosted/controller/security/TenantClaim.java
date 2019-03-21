package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * A claim for ownership of some tenant by some identity.
 *
 * @author jonmv
 */
public abstract class TenantClaim {

    private final TenantName tenant;
    private final Principal user;

    protected TenantClaim(TenantName tenant, Principal user) {
        this.user = requireNonNull(user);
        this.tenant = requireNonNull(tenant);
    }

    /** The tenant this claim concerns. */
    public TenantName tenant() { return tenant; }

    /** The user making this claim. */
    public Principal user() { return user; }

}
