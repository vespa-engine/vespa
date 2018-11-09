// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A tenant that has been locked for modification. Provides methods for modifying a tenant's fields.
 *
 * @author mpolden
 */
public class LockedTenant {

    private final Lock lock;
    private final TenantName name;
    private AthenzDomain domain;
    private Property property;
    private Optional<PropertyId> propertyId;
    private final Optional<Contact> contact;
    private final boolean isAthenzTenant;

    /**
     * Should never be constructed directly.
     *
     * Use {@link TenantController#lockIfPresent(TenantName, Consumer)} or
     * {@link TenantController#lockOrThrow(TenantName, Consumer)}
     */
    LockedTenant(AthenzTenant tenant, Lock lock) {
        this(lock, tenant.name(), tenant.domain(), tenant.property(), tenant.propertyId(), tenant.contact());
    }

    LockedTenant(UserTenant tenant, Lock lock) {
        this(lock, tenant.name(), tenant.contact());
    }

    private LockedTenant(Lock lock, TenantName name, AthenzDomain domain, Property property,
                         Optional<PropertyId> propertyId, Optional<Contact> contact) {
        this.lock = Objects.requireNonNull(lock, "lock must be non-null");
        this.name = Objects.requireNonNull(name, "name must be non-null");
        this.domain = Objects.requireNonNull(domain, "domain must be non-null");
        this.property = Objects.requireNonNull(property, "property must be non-null");
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must be non-null");
        this.contact = Objects.requireNonNull(contact, "contact must be non-null");
        this.isAthenzTenant = true;
    }

    private LockedTenant(Lock lock, TenantName name, Optional<Contact> contact) {
        this.lock = Objects.requireNonNull(lock, "lock must be non-null");
        this.name = Objects.requireNonNull(name, "name must be non-null");
        this.contact = Objects.requireNonNull(contact, "contact must be non-null");
        this.isAthenzTenant = false;
    }

    /** Returns a read-only copy of this */
    public Tenant get() {
        if (isAthenzTenant) return new AthenzTenant(name, domain, property, propertyId, contact);
        else return new UserTenant(name, contact);
    }

    public LockedTenant with(AthenzDomain domain) {
        return new LockedTenant(lock, name, domain, property, propertyId, contact);
    }

    public LockedTenant with(Property property) {
        return new LockedTenant(lock, name, domain, property, propertyId, contact);
    }

    public LockedTenant with(PropertyId propertyId) {
        return new LockedTenant(lock, name, domain, property, Optional.of(propertyId), contact);
    }

    public LockedTenant with(Contact contact) {
        if (isAthenzTenant) return new LockedTenant(lock, name, domain, property, propertyId, Optional.of(contact));
        return new LockedTenant(lock, name, Optional.of(contact));
    }

    @Override
    public String toString() {
        return "tenant '" + name + "'";
    }

}
