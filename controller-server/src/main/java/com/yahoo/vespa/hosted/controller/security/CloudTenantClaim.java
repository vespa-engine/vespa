package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;

import java.security.Principal;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the permit data of an Okta tenancy modification.
 *
 * @author jonmv
 */
public class CloudTenantClaim extends TenantClaim {

    private final String registrationToken;

    public CloudTenantClaim(TenantName tenant, String registrationToken) {
        super(tenant);
        this.registrationToken = requireNonNull(registrationToken);
    }

    /** The cloud issued token proving the user intends to register the given tenant. */
    public String getRegistrationToken() { return registrationToken; }

}
