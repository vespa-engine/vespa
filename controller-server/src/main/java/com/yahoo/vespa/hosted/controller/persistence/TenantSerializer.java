// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;


import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.Optional;

/**
 * Slime serialization of tenants.
 *
 * @author mpolden
 */
public class TenantSerializer {

    private static final String nameField = "name";
    private static final String athenzDomainField = "athenzDomain";
    private static final String propertyField = "property";
    private static final String propertyIdField = "propertyId";

    public Slime toSlime(AthenzTenant tenant) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(nameField, tenant.name().value());
        root.setString(athenzDomainField, tenant.domain().getName());
        root.setString(propertyField, tenant.property().id());
        tenant.propertyId().ifPresent(propertyId -> root.setString(propertyIdField, propertyId.id()));
        return slime;
    }

    public Slime toSlime(UserTenant tenant) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(nameField, tenant.name().value());
        return slime;
    }

    public AthenzTenant athenzTenantFrom(Slime slime) {
        Inspector root = slime.get();
        TenantName name = TenantName.from(root.field(nameField).asString());
        AthenzDomain domain = new AthenzDomain(root.field(athenzDomainField).asString());
        Property property = new Property(root.field(propertyField).asString());
        Optional<PropertyId> propertyId = SlimeUtils.optionalString(root.field(propertyIdField)).map(PropertyId::new);
        return new AthenzTenant(name, domain, property, propertyId);
    }

    public UserTenant userTenantFrom(Slime slime) {
        Inspector root = slime.get();
        TenantName name = TenantName.from(root.field(nameField).asString());
        return new UserTenant(name);
    }

}
