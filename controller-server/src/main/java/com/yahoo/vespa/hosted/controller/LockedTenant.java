// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;

import java.util.Objects;
import java.util.Optional;

/**
 * A tenant that has been locked for modification. Provides methods for modifying a tenant's fields.
 *
 * @author mpolden
 */
public class LockedTenant {

    private final Lock lock;
    private final TenantName name;
    private final AthenzDomain domain;
    private final Property property;
    private final Optional<PropertyId> propertyId;

    LockedTenant(AthenzTenant tenant, Lock lock) {
        this(lock, tenant.name(), tenant.domain(), tenant.property(), tenant.propertyId());
    }

    private LockedTenant(Lock lock, TenantName name, AthenzDomain domain, Property property,
                         Optional<PropertyId> propertyId) {
        this.lock = Objects.requireNonNull(lock, "lock must be non-null");
        this.name = Objects.requireNonNull(name, "name must be non-null");
        this.domain = Objects.requireNonNull(domain, "domain must be non-null");
        this.property = Objects.requireNonNull(property, "property must be non-null");
        this.propertyId = Objects.requireNonNull(propertyId, "propertId must be non-null");
    }

    /** Returns a read-only copy of this */
    public AthenzTenant get() {
        return new AthenzTenant(name, domain, property, propertyId);
    }

    public LockedTenant with(AthenzDomain domain) {
        return new LockedTenant(lock, name, domain, property, propertyId);
    }

    public LockedTenant with(Property property) {
        return new LockedTenant(lock, name, domain, property, propertyId);
    }

    public LockedTenant with(PropertyId propertyId) {
        return new LockedTenant(lock, name, domain, property, Optional.of(propertyId));
    }

    @Override
    public String toString() {
        return "tenant '" + name + "'";
    }

}
