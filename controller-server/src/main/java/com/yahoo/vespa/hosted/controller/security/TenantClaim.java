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

    protected TenantClaim(TenantName tenant) {
        this.tenant = requireNonNull(tenant);
    }

    /** The tenant this claim concerns. */
    public TenantName tenant() { return tenant; }

}
