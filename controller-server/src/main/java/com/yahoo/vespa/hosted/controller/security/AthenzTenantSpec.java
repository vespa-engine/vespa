// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Extends the specification for creating an Athenz tenant.
 *
 * @author jonmv
 */
public class AthenzTenantSpec extends TenantSpec {

    private final AthenzDomain domain;
    private final Property property;
    private final Optional<PropertyId> propertyId;

    public AthenzTenantSpec(TenantName tenant, AthenzDomain domain, Property property, Optional<PropertyId> propertyId) {
        super(tenant);
        this.domain = domain;
        this.property = requireNonNull(property);
        this.propertyId = requireNonNull(propertyId);
    }

    /** The domain to create this tenant under. */
    public AthenzDomain domain() { return domain; }

    /** The property name of the tenant. */
    public Property property() { return property; }

    /** The ID of the property of the tenant. */
    public Optional<PropertyId> propertyId() { return propertyId; }

}
