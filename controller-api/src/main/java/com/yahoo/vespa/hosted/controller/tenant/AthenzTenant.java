// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an Athenz tenant in hosted Vespa.
 *
 * @author mpolden
 */
public class AthenzTenant extends Tenant {

    private final AthenzDomain domain;
    private final Property property;
    private final Optional<PropertyId> propertyId;

    /**
     * This should only be used by serialization.
     * Use {@link #create(TenantName, AthenzDomain, Property, Optional, Instant)}.
     * */
    public AthenzTenant(TenantName name, AthenzDomain domain, Property property, Optional<PropertyId> propertyId,
                        Optional<Contact> contact, Instant createdAt, LastLoginInfo lastLoginInfo) {
        super(name, createdAt, lastLoginInfo, contact);
        this.domain = Objects.requireNonNull(domain, "domain must be non-null");
        this.property = Objects.requireNonNull(property, "property must be non-null");
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must be non-null");
    }

    /** Property name of this tenant */
    public Property property() {
        return property;
    }

    /** Property ID of the tenant, if any */
    public Optional<PropertyId> propertyId() {
        return propertyId;
    }

    /** Athenz domain of this tenant */
    public AthenzDomain domain() {
        return domain;
    }

    /** Returns true if tenant is in given domain */
    public boolean in(AthenzDomain domain) {
        return this.domain.equals(domain);
    }

    @Override
    public String toString() {
        return "athenz tenant '" + name() + "'";
    }

    /** Create a new Athenz tenant */
    public static AthenzTenant create(TenantName name, AthenzDomain domain, Property property,
                                      Optional<PropertyId> propertyId, Instant createdAt) {
        return new AthenzTenant(requireName(name), domain, property, propertyId, Optional.empty(), createdAt, LastLoginInfo.EMPTY);
    }

    @Override
    public Type type() {
        return Type.athenz;
    }

}
