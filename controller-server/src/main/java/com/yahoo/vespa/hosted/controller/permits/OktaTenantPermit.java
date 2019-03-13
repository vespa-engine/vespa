package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.TenantName;

import java.security.Principal;
import java.util.Objects;

/**
 * Wraps the permit data of an Okta tenancy modification.
 *
 * @author jonmv
 */
public class OktaTenantPermit implements TenantPermit {

    private final TenantName tenant;
    private final Principal user;

    public OktaTenantPermit(TenantName tenant, Principal user) {
        this.tenant = Objects.requireNonNull(tenant);
        this.user = Objects.requireNonNull(user);
    }

    public TenantName tenant() { return tenant; }
    public Principal user() { return user; }

}
