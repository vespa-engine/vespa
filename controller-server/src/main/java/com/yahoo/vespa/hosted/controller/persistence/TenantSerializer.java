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
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoAddress;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoBillingContact;

import java.net.URI;
import java.security.Principal;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String creatorField = "creator";
    private static final String createdAtField = "createdAt";
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
    private static final String tenantInfoField = "info";
    private static final String lastLoginInfoField = "lastLoginInfo";
    private static final String secretStoresField = "secretStores";
    private static final String archiveAccessRoleField = "archiveAccessRole";
    private static final String awsIdField = "awsId";
    private static final String roleField = "role";

    public Slime toSlime(Tenant tenant) {
        Slime slime = new Slime();
        Cursor tenantObject = slime.setObject();
        tenantObject.setString(nameField, tenant.name().value());
        tenantObject.setString(typeField, valueOf(tenant.type()));
        tenantObject.setLong(createdAtField, tenant.createdAt().toEpochMilli());
        toSlime(tenant.lastLoginInfo(), tenantObject.setObject(lastLoginInfoField));

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
        // BillingInfo was never used and always just a static default value.  To retire this
        // field we continue to write the default value and stop reading it.
        // TODO(ogronnesby, 2020-08-05): Remove when a version where we do not read the field has propagated.
        var legacyBillingInfo = new BillingInfo("customer", "Vespa");
        tenant.creator().ifPresent(creator -> root.setString(creatorField, creator.getName()));
        developerKeysToSlime(tenant.developerKeys(), root.setArray(pemDeveloperKeysField));
        toSlime(legacyBillingInfo, root.setObject(billingInfoField));
        toSlime(tenant.info(), root);
        toSlime(tenant.tenantSecretStores(), root);
        tenant.archiveAccessRole().ifPresent(role -> root.setString(archiveAccessRoleField, role));
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

    private void toSlime(LastLoginInfo lastLoginInfo, Cursor lastLoginInfoObject) {
        for (LastLoginInfo.UserLevel userLevel: LastLoginInfo.UserLevel.values()) {
            lastLoginInfo.get(userLevel).ifPresent(lastLoginAt ->
                    lastLoginInfoObject.setLong(valueOf(userLevel), lastLoginAt.toEpochMilli()));
        }
    }

    public Tenant tenantFrom(Slime slime) {
        Inspector tenantObject = slime.get();
        Tenant.Type type = typeOf(tenantObject.field(typeField).asString());

        switch (type) {
            case athenz: return athenzTenantFrom(tenantObject);
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
        Instant createdAt = Instant.ofEpochMilli(tenantObject.field(createdAtField).asLong());
        LastLoginInfo lastLoginInfo = lastLoginInfoFromSlime(tenantObject.field(lastLoginInfoField));
        return new AthenzTenant(name, domain, property, propertyId, contact, createdAt, lastLoginInfo);
    }

    private CloudTenant cloudTenantFrom(Inspector tenantObject) {
        TenantName name = TenantName.from(tenantObject.field(nameField).asString());
        Instant createdAt = Instant.ofEpochMilli(tenantObject.field(createdAtField).asLong());
        LastLoginInfo lastLoginInfo = lastLoginInfoFromSlime(tenantObject.field(lastLoginInfoField));
        Optional<Principal> creator = SlimeUtils.optionalString(tenantObject.field(creatorField)).map(SimplePrincipal::new);
        BiMap<PublicKey, Principal> developerKeys = developerKeysFromSlime(tenantObject.field(pemDeveloperKeysField));
        TenantInfo info = tenantInfoFromSlime(tenantObject.field(tenantInfoField));
        List<TenantSecretStore> tenantSecretStores = secretStoresFromSlime(tenantObject.field(secretStoresField));
        Optional<String> archiveAccessRole = SlimeUtils.optionalString(tenantObject.field(archiveAccessRoleField));
        return new CloudTenant(name, createdAt, lastLoginInfo, creator, developerKeys, info, tenantSecretStores, archiveAccessRole);
    }

    private BiMap<PublicKey, Principal> developerKeysFromSlime(Inspector array) {
        ImmutableBiMap.Builder<PublicKey, Principal> keys = ImmutableBiMap.builder();
        array.traverse((ArrayTraverser) (__, keyObject) ->
                keys.put(KeyUtils.fromPemEncodedPublicKey(keyObject.field("key").asString()),
                         new SimplePrincipal(keyObject.field("user").asString())));

        return keys.build();
    }

    TenantInfo tenantInfoFromSlime(Inspector infoObject) {
        if (!infoObject.valid()) return TenantInfo.EMPTY;

        return TenantInfo.EMPTY
                .withName(infoObject.field("name").asString())
                .withEmail(infoObject.field("email").asString())
                .withWebsite(infoObject.field("website").asString())
                .withContactName(infoObject.field("contactName").asString())
                .withContactEmail(infoObject.field("contactEmail").asString())
                .withInvoiceEmail(infoObject.field("invoiceEmail").asString())
                .withAddress(tenantInfoAddressFromSlime(infoObject.field("address")))
                .withBillingContact(tenantInfoBillingContactFromSlime(infoObject.field("billingContact")));
    }

    private TenantInfoAddress tenantInfoAddressFromSlime(Inspector addressObject) {
        return TenantInfoAddress.EMPTY
                .withAddressLines(addressObject.field("addressLines").asString())
                .withPostalCodeOrZip(addressObject.field("postalCodeOrZip").asString())
                .withCity(addressObject.field("city").asString())
                .withStateRegionProvince(addressObject.field("stateRegionProvince").asString())
                .withCountry(addressObject.field("country").asString());
    }

    private TenantInfoBillingContact tenantInfoBillingContactFromSlime(Inspector billingObject) {
        return TenantInfoBillingContact.EMPTY
                .withName(billingObject.field("name").asString())
                .withEmail(billingObject.field("email").asString())
                .withPhone(billingObject.field("phone").asString())
                .withAddress(tenantInfoAddressFromSlime(billingObject.field("address")));
    }

    private List<TenantSecretStore> secretStoresFromSlime(Inspector secretStoresObject) {
        List<TenantSecretStore> secretStores = new ArrayList<>();
        if (!secretStoresObject.valid()) return secretStores;

        secretStoresObject.traverse((ArrayTraverser) (index, inspector) -> {
            secretStores.add(
                    new TenantSecretStore(
                            inspector.field(nameField).asString(),
                            inspector.field(awsIdField).asString(),
                            inspector.field(roleField).asString()
                    )
            );
        });
        return secretStores;
    }

    private LastLoginInfo lastLoginInfoFromSlime(Inspector lastLoginInfoObject) {
        Map<LastLoginInfo.UserLevel, Instant> lastLoginByUserLevel = new HashMap<>();
        lastLoginInfoObject.traverse((String name, Inspector value) ->
                lastLoginByUserLevel.put(userLevelOf(name), Instant.ofEpochMilli(value.asLong())));
        return new LastLoginInfo(lastLoginByUserLevel);
    }

    void toSlime(TenantInfo info, Cursor parentCursor) {
        if (info.isEmpty()) return;
        Cursor infoCursor = parentCursor.setObject("info");
        infoCursor.setString("name", info.name());
        infoCursor.setString("email", info.email());
        infoCursor.setString("website", info.website());
        infoCursor.setString("invoiceEmail", info.invoiceEmail());
        infoCursor.setString("contactName", info.contactName());
        infoCursor.setString("contactEmail", info.contactEmail());
        toSlime(info.address(), infoCursor);
        toSlime(info.billingContact(), infoCursor);
    }

    private void toSlime(TenantInfoAddress address, Cursor parentCursor) {
        if (address.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("address");
        addressCursor.setString("addressLines", address.addressLines());
        addressCursor.setString("postalCodeOrZip", address.postalCodeOrZip());
        addressCursor.setString("city", address.city());
        addressCursor.setString("stateRegionProvince", address.stateRegionProvince());
        addressCursor.setString("country", address.country());
    }

    private void toSlime(TenantInfoBillingContact billingContact, Cursor parentCursor) {
        if (billingContact.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("billingContact");
        addressCursor.setString("name", billingContact.name());
        addressCursor.setString("email", billingContact.email());
        addressCursor.setString("phone", billingContact.phone());
        toSlime(billingContact.address(), addressCursor);
    }

    private void toSlime(List<TenantSecretStore> tenantSecretStores, Cursor parentCursor) {
        if (tenantSecretStores.isEmpty()) return;

        Cursor secretStoresCursor = parentCursor.setArray(secretStoresField);
        tenantSecretStores.forEach(tenantSecretStore -> {
            Cursor secretStoreCursor = secretStoresCursor.addObject();
            secretStoreCursor.setString(nameField, tenantSecretStore.getName());
            secretStoreCursor.setString(awsIdField, tenantSecretStore.getAwsId());
            secretStoreCursor.setString(roleField, tenantSecretStore.getRole());
        });

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
            case "cloud":  return Tenant.Type.cloud;
            default: throw new IllegalArgumentException("Unknown tenant type '" + value + "'.");
        }
    }

    private static String valueOf(Tenant.Type type) {
        switch (type) {
            case athenz: return "athenz";
            case cloud:  return "cloud";
            default: throw new IllegalArgumentException("Unexpected tenant type '" + type + "'.");
        }
    }

    private static LastLoginInfo.UserLevel userLevelOf(String value) {
        switch (value) {
            case "user": return LastLoginInfo.UserLevel.user;
            case "developer": return LastLoginInfo.UserLevel.developer;
            case "administrator": return LastLoginInfo.UserLevel.administrator;
            default: throw new IllegalArgumentException("Unknown user level '" + value + "'.");
        }
    }

    private static String valueOf(LastLoginInfo.UserLevel userLevel) {
        switch (userLevel) {
            case user: return "user";
            case developer: return "developer";
            case administrator: return "administrator";
            default: throw new IllegalArgumentException("Unexpected user level '" + userLevel + "'.");
        }
    }
}
