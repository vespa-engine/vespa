// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

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
     * Use {@link #create(TenantName, AthenzDomain, Property, Optional)}.
     * */
    public AthenzTenant(TenantName name, AthenzDomain domain, Property property, Optional<PropertyId> propertyId) {
        super(name);
        this.domain = domain;
        this.property = property;
        this.propertyId = propertyId;
    }

    /** Property name of this tenant */
    public Property property() {
        return property;
    }

    /** Property ID of the tenant, if present */
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

    public AthenzTenant with(AthenzDomain domain) {
        return new AthenzTenant(name(), domain, property(), propertyId());
    }

    public AthenzTenant with(Property property) {
        return new AthenzTenant(name(), domain, property, propertyId());
    }

    public AthenzTenant with(PropertyId propertyId) {
        return new AthenzTenant(name(), domain, property, Optional.of(propertyId));
    }

    /** Create a new Athenz tenant */
    public static AthenzTenant create(TenantName name, AthenzDomain domain, Property property,
                                      Optional<PropertyId> propertyId) {
        return new AthenzTenant(requireName(requireNoPrefix(name)), domain, property, propertyId);
    }

    private static TenantName requireNoPrefix(TenantName name) {
        if (name.value().startsWith(Tenant.userPrefix)) {
            throw new IllegalArgumentException("Athenz tenant name cannot have prefix '" + Tenant.userPrefix + "'");
        }
        return name;
    }
}
