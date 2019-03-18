// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A tenant that has been locked for modification. Provides methods for modifying a tenant's fields.
 *
 * @author mpolden
 * @author jonmv
 */
public abstract class LockedTenant {

    final TenantName name;

    private LockedTenant(TenantName name) {
        this.name = requireNonNull(name);
    }

    static LockedTenant of(Tenant tenant, Lock lock) {
        switch (tenant.type()) {
            case athenz: return new Athenz((AthenzTenant) tenant);
            case user:   return new User((UserTenant) tenant);
            case cloud:  return new Cloud((CloudTenant) tenant);
            default:     throw new IllegalArgumentException("Unexpected tenant type '" + tenant.getClass().getName() + "'.");
        }
    }

    /** Returns a read-only copy of this */
    public abstract Tenant get();

    @Override
    public String toString() {
        return "tenant '" + name + "'";
    }


    /** A locked AthenzTenant. */
    public static class Athenz extends LockedTenant {

        private final AthenzDomain domain;
        private final Property property;
        private final Optional<PropertyId> propertyId;
        private final Optional<Contact> contact;

        private Athenz(TenantName name, AthenzDomain domain, Property property, Optional<PropertyId> propertyId, Optional<Contact> contact) {
            super(name);
            this.domain = domain;
            this.property = property;
            this.propertyId = propertyId;
            this.contact = contact;
        }

        private Athenz(AthenzTenant tenant) {
            this(tenant.name(), tenant.domain(), tenant.property(), tenant.propertyId(), tenant.contact());
        }

        @Override
        public AthenzTenant get() {
            return new AthenzTenant(name, domain, property, propertyId, contact);
        }

        public Athenz with(AthenzDomain domain) {
            return new Athenz(name, domain, property, propertyId, contact);
        }

        public Athenz with(Property property) {
            return new Athenz(name, domain, property, propertyId, contact);
        }

        public Athenz with(PropertyId propertyId) {
            return new Athenz(name, domain, property, Optional.of(propertyId), contact);
        }

        public Athenz with(Contact contact) {
            return new Athenz(name, domain, property, propertyId, Optional.of(contact));
        }

    }


    /** A locked UserTenant. */
    public static class User extends LockedTenant {

        private final Optional<Contact> contact;

        private User(TenantName name, Optional<Contact> contact) {
            super(name);
            this.contact = contact;
        }

        private User(UserTenant tenant) {
            this(tenant.name(), tenant.contact());
        }

        @Override
        public UserTenant get() {
            return new UserTenant(name, contact);
        }

        public User with(Contact contact) {
            return new User(name, Optional.of(contact));
        }

    }


    /** A locked CloudTenant. */
    public static class Cloud extends LockedTenant {

        private final BillingInfo billingInfo;

        private Cloud(TenantName name, BillingInfo billingInfo) {
            super(name);
            this.billingInfo = billingInfo;
        }

        private Cloud(CloudTenant tenant) {
            this(tenant.name(), tenant.billingInfo());
        }

        @Override
        public CloudTenant get() {
            return new CloudTenant(name, billingInfo);
        }

        public Cloud with(BillingInfo billingInfo) {
            return new Cloud(name, billingInfo);
        }

    }

}
