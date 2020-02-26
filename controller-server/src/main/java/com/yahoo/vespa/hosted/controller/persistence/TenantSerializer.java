// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.KeyUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.net.URI;
import java.security.Principal;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Slime serialization of {@link Tenant} sub-types.
 *
 * @author mpolden
 */
public class TenantSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String nameField = "name";
    private static final String typeField = "type";
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
    private static final String billingInfoField = "billingInfo";
    private static final String customerIdField = "customerId";
    private static final String productCodeField = "productCode";
    private static final String pemDeveloperKeysField = "pemDeveloperKeys";

    public Slime toSlime(Tenant tenant) {
        Slime slime = new Slime();
        Cursor tenantObject = slime.setObject();
        tenantObject.setString(nameField, tenant.name().value());
        tenantObject.setString(typeField, valueOf(tenant.type()));

        switch (tenant.type()) {
            case athenz: toSlime((AthenzTenant) tenant, tenantObject); break;
            case cloud:  toSlime((CloudTenant) tenant, tenantObject);  break;
            default:     throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        return slime;
    }

    private void toSlime(AthenzTenant tenant, Cursor tenantObject) {
        tenantObject.setString(athenzDomainField, tenant.domain().getName());
        tenantObject.setString(propertyField, tenant.property().id());
        tenant.propertyId().ifPresent(propertyId -> tenantObject.setString(propertyIdField, propertyId.id()));
        tenant.contact().ifPresent(contact -> {
            Cursor contactCursor = tenantObject.setObject(contactField);
            writeContact(contact, contactCursor);
        });
    }

    private void toSlime(CloudTenant tenant, Cursor root) {
        developerKeysToSlime(tenant.developerKeys(), root.setArray(pemDeveloperKeysField));
        toSlime(tenant.billingInfo(), root.setObject(billingInfoField));
    }

    private void developerKeysToSlime(BiMap<PublicKey, Principal> keys, Cursor array) {
        keys.forEach((key, user) -> {
            Cursor object = array.addObject();
            object.setString("key", KeyUtils.toPem(key));
            object.setString("user", user.getName());
        });
    }

    private void toSlime(BillingInfo billingInfo, Cursor billingInfoObject) {
        billingInfoObject.setString(customerIdField, billingInfo.customerId());
        billingInfoObject.setString(productCodeField, billingInfo.productCode());
    }

    public Tenant tenantFrom(Slime slime) {
        Inspector tenantObject = slime.get();
        Tenant.Type type;
        type = typeOf(tenantObject.field(typeField).asString());

        switch (type) {
            case athenz: return athenzTenantFrom(tenantObject);
            case user:   return null; // TODO jonmv: Remove when run once.
            case cloud:  return cloudTenantFrom(tenantObject);
            default:     throw new IllegalArgumentException("Unexpected tenant type '" + type + "'.");
        }
    }

    private AthenzTenant athenzTenantFrom(Inspector tenantObject) {
        TenantName name = TenantName.from(tenantObject.field(nameField).asString());
        AthenzDomain domain = new AthenzDomain(tenantObject.field(athenzDomainField).asString());
        Property property = new Property(tenantObject.field(propertyField).asString());
        Optional<PropertyId> propertyId = SlimeUtils.optionalString(tenantObject.field(propertyIdField)).map(PropertyId::new);
        Optional<Contact> contact = contactFrom(tenantObject.field(contactField));
        return new AthenzTenant(name, domain, property, propertyId, contact);
    }

    private CloudTenant cloudTenantFrom(Inspector tenantObject) {
        TenantName name = TenantName.from(tenantObject.field(nameField).asString());
        BillingInfo billingInfo = billingInfoFrom(tenantObject.field(billingInfoField));
        BiMap<PublicKey, Principal> developerKeys = developerKeysFromSlime(tenantObject.field(pemDeveloperKeysField));
        return new CloudTenant(name, billingInfo, developerKeys);
    }

    private BiMap<PublicKey, Principal> developerKeysFromSlime(Inspector array) {
        ImmutableBiMap.Builder<PublicKey, Principal> keys = ImmutableBiMap.builder();
        array.traverse((ArrayTraverser) (__, keyObject) ->
                keys.put(KeyUtils.fromPemEncodedPublicKey(keyObject.field("key").asString()),
                         new SimplePrincipal(keyObject.field("user").asString())));

        return keys.build();
    }

    private Optional<Contact> contactFrom(Inspector object) {
        if ( ! object.valid()) return Optional.empty();

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

    private BillingInfo billingInfoFrom(Inspector billingInfoObject) {
        return new BillingInfo(billingInfoObject.field(customerIdField).asString(),
                               billingInfoObject.field(productCodeField).asString());
    }

    private static Tenant.Type typeOf(String value) {
        switch (value) {
            case "athenz": return Tenant.Type.athenz;
            case "user":   return Tenant.Type.user;
            case "cloud":  return Tenant.Type.cloud;
            default: throw new IllegalArgumentException("Unknown tenant type '" + value + "'.");
        }
    }

    private static String valueOf(Tenant.Type type) {
        switch (type) {
            case athenz: return "athenz";
            case user:   return "user";
            case cloud:  return "cloud";
            default: throw new IllegalArgumentException("Unexpected tenant type '" + type + "'.");
        }
    }

}
