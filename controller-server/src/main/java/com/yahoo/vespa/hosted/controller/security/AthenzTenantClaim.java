package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.security.Principal;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the claim data for creating an Athenz tenant.
 *
 * @author jonmv
 */
public class AthenzTenantClaim extends TenantClaim {

    private final Optional<Property> property;
    private final Optional<PropertyId> propertyId;

    public AthenzTenantClaim(TenantName tenant, Optional<AthenzDomain> domain,
                             Optional<Property> property, Optional<PropertyId> propertyId) {
        super(tenant);
        this.property = requireNonNull(property);
        this.propertyId = requireNonNull(propertyId);
    }

    /** The property name of the tenant to create. */
    public Optional<Property> property() { return property; }

    /** The ID of the property of the tenant to create. */
    public Optional<PropertyId> propertyId() { return propertyId; }

}
