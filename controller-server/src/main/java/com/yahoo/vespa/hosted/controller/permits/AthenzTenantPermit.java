package com.yahoo.vespa.hosted.controller.permits;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.security.Principal;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Wraps the permit data for creating an Athenz tenant.
 *
 * @author jonmv
 */
public class AthenzTenantPermit extends TenantPermit {

    private final Optional<Property> property;
    private final Optional<PropertyId> propertyId;
    private final Optional<AthenzDomain> domain;
    private final OktaAccessToken token;

    public AthenzTenantPermit(TenantName tenant, Principal user, Optional<AthenzDomain> domain,
                              Optional<Property> property, Optional<PropertyId> propertyId, OktaAccessToken token) {
        super(tenant, user);
        this.domain = requireNonNull(domain);
        this.token = requireNonNull(token);
        this.property = requireNonNull(property);
        this.propertyId = requireNonNull(propertyId);
    }

    /** The property name of the tenant to create. */
    public Optional<Property> property() { return property; }

    /** The ID of the property of the tenant to create. */
    public Optional<PropertyId> propertyId() { return propertyId; }

    /** The Athens domain of the concerned tenant. */
    public Optional<AthenzDomain> domain() { return domain; }

    /** The Okta issued token proving the user's access to Athenz. */
    public OktaAccessToken token() { return token; }
}
