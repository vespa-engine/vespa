// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api;

import com.yahoo.vespa.hosted.controller.api.application.v4.model.TenantType;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;

import java.util.Optional;

/**
 * @author smorgrav
 */
// TODO: Move this and everything it owns to com.yahoo.hosted.controller.Tenant and com.yahoo.hosted.controller.tenant.*
public class Tenant {

    private final TenantId id;
    private final Optional<UserGroup> userGroup;
    private final Optional<Property> property;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<PropertyId> propertyId;

    // TODO: Use factory methods. They're down at the bottom!
    public Tenant(TenantId id, Optional<UserGroup> userGroup, Optional<Property> property, Optional<AthenzDomain> athenzDomain) {
        this(id, userGroup, property, athenzDomain, Optional.empty());
    }

    public Tenant(TenantId id, Optional<UserGroup> userGroup, Optional<Property> property, Optional<AthenzDomain> athenzDomain, Optional<PropertyId> propertyId) {
        if (id.isUser()) {
            require( ! userGroup.isPresent(), "User tenant '%s' cannot have a user group.", id);
            require( ! property.isPresent(), "User tenant '%s' cannot have a property.", id);
            require( ! propertyId.isPresent(), "User tenant '%s' cannot have a property ID.", id);
            require( ! athenzDomain.isPresent(), "User tenant '%s' cannot have an athens domain.", id);
        } else if (athenzDomain.isPresent()) {
            require(   property.isPresent(), "Athens tenant '%s' must have a property.", id);
            require( ! userGroup.isPresent(), "Athens tenant '%s' cannot have a user group.", id);
            require(   athenzDomain.isPresent(), "Athens tenant '%s' must have an athens domain.", id);
        } else {
            require(   property.isPresent(), "OpsDB tenant '%s' must have a property.", id);
            require(   userGroup.isPresent(), "OpsDb tenant '%s' must have a user group.", id);
            require( ! athenzDomain.isPresent(), "OpsDb tenant '%s' cannot have an athens domain.", id);
        }
        this.id = id;
        this.userGroup = userGroup;
        this.property = property;
        this.athenzDomain = athenzDomain;
        this.propertyId = propertyId; // TODO: Check validity after TODO@14. OpsDb tenants have this set in Sherpa, while athens tenants do not.
        // TODO: Require PropertyId for non-users, and fetch Property from EntityService (which will be moved to Organization) in the controller.
    }

    public boolean isAthensTenant() { return athenzDomain.isPresent(); }
    public boolean isOpsDbTenant() { return userGroup.isPresent();}

    public TenantType tenantType() {
        if (athenzDomain.isPresent()) {
            return TenantType.ATHENS;
        } else if (id.isUser()) {
            return TenantType.USER;
        } else {
            return TenantType.OPSDB;
        }
    }

    public TenantId getId() {
        return id;
    }

    public Optional<UserGroup> getUserGroup() {
        return userGroup;
    }

    /** OpsDB property name of the tenant, or Optional.empty() if none is stored. */
    public Optional<Property> getProperty() {
        return property;
    }

    /** OpsDB property ID of the tenant. Not (yet) required, so returns Optional.empty() if none is stored. */
    public Optional<PropertyId> getPropertyId() {
        return propertyId;
    }

    public Optional<AthenzDomain> getAthensDomain() {
        return athenzDomain;
    }

    private void require(boolean statement, String message, TenantId id) {
        if (!statement) throw new IllegalArgumentException(String.format(message, id));
    }

    public static Tenant createAthensTenant(TenantId id, AthenzDomain athensDomain, Property property, Optional<PropertyId> propertyId) {
        if (id.isUser()) {
            throw new IllegalArgumentException("Invalid id for non-user tenant: " + id);
        }
        return new Tenant(id, Optional.empty(), Optional.ofNullable(property),
                          Optional.ofNullable(athensDomain), propertyId);
    }

    public static Tenant createOpsDbTenant(TenantId id, UserGroup userGroup, Property property, Optional<PropertyId> propertyId) {
        if (id.isUser()) {
            throw new IllegalArgumentException("Invalid id for non-user tenant: " + id);
        }
        return new Tenant(id, Optional.ofNullable(userGroup), Optional.ofNullable(property), Optional.empty(), propertyId);
    }

    public static Tenant createOpsDbTenant(TenantId id, UserGroup userGroup, Property property) {
        return createOpsDbTenant(id, userGroup, property, Optional.empty());
    }

    public static Tenant createUserTenant(TenantId id) {
        if (!id.isUser()) {
            throw new IllegalArgumentException("Invalid id for user tenant: " + id);
        }
        return new Tenant(id, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tenant tenant = (Tenant) o;

        if (!id.equals(tenant.id)) return false;
        if (!userGroup.equals(tenant.userGroup)) return false;
        if (!property.equals(tenant.property)) return false;
        if (!athenzDomain.equals(tenant.athenzDomain)) return false;
        if (!propertyId.equals(tenant.propertyId)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + userGroup.hashCode();
        result = 31 * result + property.hashCode();
        result = 31 * result + athenzDomain.hashCode();
        result = 31 * result + propertyId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "tenant '" + id + "'";
    }

}
