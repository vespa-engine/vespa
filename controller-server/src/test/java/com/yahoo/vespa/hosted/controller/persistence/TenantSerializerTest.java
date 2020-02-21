// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.TenantName;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.role.SimplePrincipal;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import org.junit.Test;

import java.net.URI;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void athenz_tenant() {
        AthenzTenant tenant = AthenzTenant.create(TenantName.from("athenz-tenant"),
                                                  new AthenzDomain("domain1"),
                                                  new Property("property1"),
                                                  Optional.of(new PropertyId("1")));
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.name(), serialized.name());
        assertEquals(tenant.domain(), serialized.domain());
        assertEquals(tenant.property(), serialized.property());
        assertTrue(serialized.propertyId().isPresent());
        assertEquals(tenant.propertyId(), serialized.propertyId());
    }

    @Test
    public void athenz_tenant_without_property_id() {
        AthenzTenant tenant = AthenzTenant.create(TenantName.from("athenz-tenant"),
                                                             new AthenzDomain("domain1"),
                                                             new Property("property1"),
                                                             Optional.empty());
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertFalse(serialized.propertyId().isPresent());
        assertEquals(tenant.propertyId(), serialized.propertyId());
    }

    @Test
    public void athenz_tenant_with_contact() {
        AthenzTenant tenant = new AthenzTenant(TenantName.from("athenz-tenant"),
                                               new AthenzDomain("domain1"),
                                               new Property("property1"),
                                               Optional.of(new PropertyId("1")),
                                               Optional.of(contact()));
        AthenzTenant serialized = (AthenzTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.contact(), serialized.contact());
    }

    @Test
    public void cloud_tenant() {
        CloudTenant tenant = new CloudTenant(TenantName.from("elderly-lady"),
                                             new BillingInfo("old cat lady", "vespa"),
                                             ImmutableBiMap.of(publicKey, new SimplePrincipal("joe"),
                                                               otherPublicKey, new SimplePrincipal("jane")));
        CloudTenant serialized = (CloudTenant) serializer.tenantFrom(serializer.toSlime(tenant));
        assertEquals(tenant.name(), serialized.name());
        assertEquals(tenant.billingInfo(), serialized.billingInfo());
        assertEquals(tenant.developerKeys(), serialized.developerKeys());
    }

    private Contact contact() {
        return new Contact(
                URI.create("http://contact1.test"),
                URI.create("http://property1.test"),
                URI.create("http://issue-tracker-1.test"),
                List.of(
                        Collections.singletonList("person1"),
                        Collections.singletonList("person2")
                ),
                "queue",
                Optional.empty()
        );
    }

}
