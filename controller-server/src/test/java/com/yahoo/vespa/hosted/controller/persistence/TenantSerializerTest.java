// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.KeyUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.DeletedTenant;
import com.yahoo.vespa.hosted.controller.tenant.Email;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantAddress;
import com.yahoo.vespa.hosted.controller.tenant.TenantBilling;
import com.yahoo.vespa.hosted.controller.tenant.TenantContact;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mpolden
 */
public class TenantSerializerTest {

    private static final TenantSerializer serializer = new TenantSerializer();
    private static final PublicKey publicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                                                "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                                                "-----END PUBLIC KEY-----\n");
    private static final PublicKey otherPublicKey = KeyUtils.fromPemEncodedPublicKey("-----BEGIN PUBLIC KEY-----\n" +
                                                                                     "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\n" +
                                                                                     "pDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\n" +
                                                                                     "-----END PUBLIC KEY-----\n");

    @Test
    void athenz_tenant() {
        AthenzTenant tenant = AthenzTenant.create(TenantName.from("athenz-tenant"),
                new AthenzDomain("domain1"),
                new Property("property1"),
                Optional.of(new PropertyId("1")),
                Instant.ofEpochMilli(1234L));
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.name(), serialized.name());
        assertEquals(tenant.domain(), serialized.domain());
        assertEquals(tenant.property(), serialized.property());
        assertTrue(serialized.propertyId().isPresent());
        assertEquals(tenant.propertyId(), serialized.propertyId());
        assertEquals(tenant.createdAt(), serialized.createdAt());
    }

    @Test
    void athenz_tenant_without_property_id() {
        AthenzTenant tenant = AthenzTenant.create(TenantName.from("athenz-tenant"),
                new AthenzDomain("domain1"),
                new Property("property1"),
                Optional.empty(),
                Instant.EPOCH);
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertFalse(serialized.propertyId().isPresent());
        assertEquals(tenant.propertyId(), serialized.propertyId());
    }

    @Test
    void athenz_tenant_with_contact() {
        AthenzTenant tenant = new AthenzTenant(TenantName.from("athenz-tenant"),
                new AthenzDomain("domain1"),
                new Property("property1"),
                Optional.of(new PropertyId("1")),
                Optional.of(contact()),
                Instant.EPOCH,
                lastLoginInfo(321L, 654L, 987L));
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.contact(), serialized.contact());
    }

    @Test
    void cloud_tenant() {
        CloudTenant tenant = new CloudTenant(TenantName.from("elderly-lady"),
                Instant.ofEpochMilli(1234L),
                lastLoginInfo(123L, 456L, null),
                Optional.of(new SimplePrincipal("foobar-user")),
                ImmutableBiMap.of(publicKey, new SimplePrincipal("joe"),
                        otherPublicKey, new SimplePrincipal("jane")),
                TenantInfo.empty(),
                List.of(),
                new ArchiveAccess(),
                Optional.empty());
        CloudTenant serialized = (CloudTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.name(), serialized.name());
        assertEquals(tenant.creator(), serialized.creator());
        assertEquals(tenant.developerKeys(), serialized.developerKeys());
        assertEquals(tenant.createdAt(), serialized.createdAt());
    }

    @Test
    void cloud_tenant_with_info() {
        CloudTenant tenant = new CloudTenant(TenantName.from("elderly-lady"),
                Instant.EPOCH,
                lastLoginInfo(null, 789L, 654L),
                Optional.of(new SimplePrincipal("foobar-user")),
                ImmutableBiMap.of(publicKey, new SimplePrincipal("joe"),
                        otherPublicKey, new SimplePrincipal("jane")),
                TenantInfo.empty().withName("Ofni Tnanet"),
                List.of(
                        new TenantSecretStore("ss1", "123", "role1"),
                        new TenantSecretStore("ss2", "124", "role2")
                ),
                new ArchiveAccess().withAWSRole("arn:aws:iam::123456789012:role/my-role"),
                Optional.of(Instant.ofEpochMilli(1234567)));
        CloudTenant serialized = (CloudTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.info(), serialized.info());
        assertEquals(tenant.tenantSecretStores(), serialized.tenantSecretStores());
        assertEquals(tenant.invalidateUserSessionsBefore(), serialized.invalidateUserSessionsBefore());
    }

    @Test
    void cloud_tenant_with_old_archive_access_serialization() {
        var json = "{\n" +
                "  \"name\": \"elderly-lady\",\n" +
                "  \"type\": \"cloud\",\n" +
                "  \"createdAt\": 1234,\n" +
                "  \"lastLoginInfo\": {\n" +
                "    \"user\": 123,\n" +
                "    \"developer\": 456\n" +
                "  },\n" +
                "  \"creator\": \"foobar-user\",\n" +
                "  \"pemDeveloperKeys\": [\n" +
                "    {\n" +
                "      \"key\": \"-----BEGIN PUBLIC KEY-----\\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\\nz/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\\n-----END PUBLIC KEY-----\\n\",\n" +
                "      \"user\": \"joe\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"key\": \"-----BEGIN PUBLIC KEY-----\\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFELzPyinTfQ/sZnTmRp5E4Ve/sbE\\npDhJeqczkyFcT2PysJ5sZwm7rKPEeXDOhzTPCyRvbUqc2SGdWbKUGGa/Yw==\\n-----END PUBLIC KEY-----\\n\",\n" +
                "      \"user\": \"jane\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"billingInfo\": {\n" +
                "    \"customerId\": \"customer\",\n" +
                "    \"productCode\": \"Vespa\"\n" +
                "  },\n" +
                "  \"archiveAccessRole\": \"arn:aws:iam::123456789012:role/my-role\"\n" +
                "}";
        var tenant = (CloudTenant) serializer.tenantFrom(SlimeUtils.jsonToSlime(json));
        assertEquals("arn:aws:iam::123456789012:role/my-role", tenant.archiveAccess().awsRole().get());
        assertFalse(tenant.archiveAccess().gcpMember().isPresent());
    }

    @Test
    void cloud_tenant_with_archive_access() {
        CloudTenant tenant = new CloudTenant(TenantName.from("elderly-lady"),
                Instant.ofEpochMilli(1234L),
                lastLoginInfo(123L, 456L, null),
                Optional.of(new SimplePrincipal("foobar-user")),
                ImmutableBiMap.of(publicKey, new SimplePrincipal("joe"),
                        otherPublicKey, new SimplePrincipal("jane")),
                TenantInfo.empty(),
                List.of(),
                new ArchiveAccess().withAWSRole("arn:aws:iam::123456789012:role/my-role").withGCPMember("user:foo@example.com"),
                Optional.empty());
        CloudTenant serialized = (CloudTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(serialized.archiveAccess().awsRole().get(), "arn:aws:iam::123456789012:role/my-role");
        assertEquals(serialized.archiveAccess().gcpMember().get(), "user:foo@example.com");
    }

    @Test
    void cloud_tenant_with_tenant_info_partial() {
        TenantInfo partialInfo = TenantInfo.empty()
                .withAddress(TenantAddress.empty().withCity("Hønefoss"));

        Slime slime = new Slime();
        Cursor parentObject = slime.setObject();
        serializer.toSlime(partialInfo, parentObject);
        assertEquals("{\"info\":{\"name\":\"\",\"email\":\"\",\"website\":\"\",\"contactName\":\"\",\"contactEmail\":\"\",\"contactEmailVerified\":true,\"address\":{\"addressLines\":\"\",\"postalCodeOrZip\":\"\",\"city\":\"Hønefoss\",\"stateRegionProvince\":\"\",\"country\":\"\"}}}", slime.toString());
    }

    @Test
    void cloud_tenant_with_tenant_info_full() {
        TenantInfo fullInfo = TenantInfo.empty()
                .withName("My Company")
                .withEmail("email@mycomp.any")
                .withWebsite("http://mycomp.any")
                .withContact(TenantContact.from("My Name", new Email("ceo@mycomp.any", true)))
                .withAddress(TenantAddress.empty()
                        .withCity("Hønefoss")
                        .withAddress("Riperbakken 2")
                        .withCountry("Norway")
                        .withCode("3510")
                        .withRegion("Viken"))
                .withBilling(TenantBilling.empty()
                        .withContact(TenantContact.from("Thomas The Tank Engine", new Email("ceo@mycomp.any", true), "NA"))
                        .withAddress(TenantAddress.empty()
                                .withCity("Suddery")
                                .withCountry("Sodor")
                                .withAddress("Central Station")
                                .withRegion("Irish Sea")));

        Slime slime = new Slime();
        Cursor parentCursor = slime.setObject();
        serializer.toSlime(fullInfo, parentCursor);
        TenantInfo roundTripInfo = serializer.tenantInfoFromSlime(parentCursor.field("info"));

        assertEquals(fullInfo, roundTripInfo);
    }

    @Test
    void cloud_tenant_with_tenant_info_contacts() {
        TenantInfo tenantInfo = TenantInfo.empty()
                .withContacts(new TenantContacts(List.of(
                        new TenantContacts.EmailContact(List.of(TenantContacts.Audience.TENANT), new Email("email1@email.com", true)),
                        new TenantContacts.EmailContact(List.of(TenantContacts.Audience.TENANT, TenantContacts.Audience.NOTIFICATIONS), new Email("email2@email.com", true)))));
        Slime slime = new Slime();
        Cursor parentCursor = slime.setObject();
        serializer.toSlime(tenantInfo, parentCursor);
        TenantInfo roundTripInfo = serializer.tenantInfoFromSlime(parentCursor.field("info"));
        assertEquals(tenantInfo, roundTripInfo);
    }

    @Test
    void deleted_tenant() {
        DeletedTenant tenant = new DeletedTenant(
                TenantName.from("tenant1"), Instant.ofEpochMilli(1234L), Instant.ofEpochMilli(2345L));
        DeletedTenant serialized = (DeletedTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.name(), serialized.name());
        assertEquals(tenant.createdAt(), serialized.createdAt());
        assertEquals(tenant.deletedAt(), serialized.deletedAt());
    }

    private static Contact contact() {
        return new Contact(
                URI.create("http://contact1.test"),
                URI.create("http://property1.test"),
                URI.create("http://issue-tracker-1.test"),
                List.of(
                        List.of("person1"),
                        List.of("person2")
                ),
                "queue",
                Optional.empty()
        );
    }

    private static LastLoginInfo lastLoginInfo(Long user, Long developer, Long administrator) {
        Map<LastLoginInfo.UserLevel, Instant> lastLogins = new HashMap<>();
        Optional.ofNullable(user).map(Instant::ofEpochMilli).ifPresent(i -> lastLogins.put(LastLoginInfo.UserLevel.user, i));
        Optional.ofNullable(developer).map(Instant::ofEpochMilli).ifPresent(i -> lastLogins.put(LastLoginInfo.UserLevel.developer, i));
        Optional.ofNullable(administrator).map(Instant::ofEpochMilli).ifPresent(i -> lastLogins.put(LastLoginInfo.UserLevel.administrator, i));
        return new LastLoginInfo(lastLogins);
    }
}
