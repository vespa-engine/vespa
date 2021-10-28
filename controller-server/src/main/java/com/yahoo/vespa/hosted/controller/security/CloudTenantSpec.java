// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;

import static java.util.Objects.requireNonNull;

/**
 * Extends the specification for creating a cloud tenant.
 *
 * @author jonmv
 */
public class CloudTenantSpec extends TenantSpec {

    private final String registrationToken;

    public CloudTenantSpec(TenantName tenant, String registrationToken) {
        super(tenant);
        this.registrationToken = requireNonNull(registrationToken);
    }

    /** The cloud issued token proving the user intends to register the given tenant. */
    public String getRegistrationToken() { return registrationToken; }

}
