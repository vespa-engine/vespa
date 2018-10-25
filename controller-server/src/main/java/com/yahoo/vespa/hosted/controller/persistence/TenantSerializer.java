// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;


import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
    private static final String contactField = "contact";
    private static final String contactUrlField = "contactUrl";
    private static final String propertyUrlField = "propertyUrl";
    private static final String issueTrackerUrlField = "issueTrackerUrl";
    private static final String personsField = "persons";
    private static final String personField = "person";
    private static final String queueField = "queue";
    private static final String componentField = "component";

    public Slime toSlime(Tenant tenant) {
        if (tenant instanceof AthenzTenant) return toSlime((AthenzTenant) tenant);
        return toSlime((UserTenant) tenant);
    }

    private Slime toSlime(AthenzTenant tenant) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(nameField, tenant.name().value());
        root.setString(athenzDomainField, tenant.domain().getName());
        root.setString(propertyField, tenant.property().id());
        tenant.propertyId().ifPresent(propertyId -> root.setString(propertyIdField, propertyId.id()));
        tenant.contact().ifPresent(contact -> {
            Cursor contactCursor = root.setObject(contactField);
            writeContact(contact, contactCursor);
        });
        return slime;
    }

    private Slime toSlime(UserTenant tenant) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(nameField, tenant.name().value());
        tenant.contact().ifPresent(contact -> {
            Cursor contactCursor = root.setObject(contactField);
            writeContact(contact, contactCursor);
        });
        return slime;
    }

    public AthenzTenant athenzTenantFrom(Slime slime) {
        Inspector root = slime.get();
        TenantName name = TenantName.from(root.field(nameField).asString());
        AthenzDomain domain = new AthenzDomain(root.field(athenzDomainField).asString());
        Property property = new Property(root.field(propertyField).asString());
        Optional<PropertyId> propertyId = SlimeUtils.optionalString(root.field(propertyIdField)).map(PropertyId::new);
        Optional<Contact> contact = contactFrom(root.field(contactField));
        return new AthenzTenant(name, domain, property, propertyId, contact);
    }

    public UserTenant userTenantFrom(Slime slime) {
        Inspector root = slime.get();
        TenantName name = TenantName.from(root.field(nameField).asString());
        Optional<Contact> contact = contactFrom(root.field(contactField));
        return new UserTenant(name, contact);
    }

    private Optional<Contact> contactFrom(Inspector object) {
        if (!object.valid()) {
            return Optional.empty();
        }
        URI contactUrl = URI.create(object.field(contactUrlField).asString());
        URI propertyUrl = URI.create(object.field(propertyUrlField).asString());
        URI issueTrackerUrl = URI.create(object.field(issueTrackerUrlField).asString());
        List<List<String>> persons = personsFrom(object.field(personsField));
        String queue = object.field(queueField).asString();
        Optional<String> component = object.field(componentField).valid() ? Optional.of(object.field(componentField).asString()) : Optional.empty();
        return Optional.of(new Contact(contactUrl,
                                        propertyUrl,
                                        issueTrackerUrl,
                                        persons,
                                        queue,
                                        component));
    }

    private void writeContact(Contact contact, Cursor contactCursor) {
        contactCursor.setString(contactUrlField, contact.url().toString());
        contactCursor.setString(propertyUrlField, contact.propertyUrl().toString());
        contactCursor.setString(issueTrackerUrlField, contact.issueTrackerUrl().toString());
        Cursor personsArray = contactCursor.setArray(personsField);
        contact.persons().forEach(personList -> {
            Cursor personArray = personsArray.addArray();
            personList.forEach(person -> {
                Cursor personObject = personArray.addObject();
                personObject.setString(personField, person);
            });
        });
        contactCursor.setString(queueField, contact.queue());
        contact.component().ifPresent(component -> contactCursor.setString(componentField, component));
    }

    private List<List<String>> personsFrom(Inspector array) {
        List<List<String>> personLists = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, personArray) -> {
            List<String> persons = new ArrayList<>();
            personArray.traverse((ArrayTraverser) (j, inspector) -> persons.add(inspector.field("person").asString()));
            personLists.add(persons);
        });
        return personLists;
    }

}
