// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author mpolden
 */
public class ContactInformationMaintainerTest {

    private ControllerTester tester;
    private ContactInformationMaintainer maintainer;

    @BeforeEach
    public void before() {
        tester = new ControllerTester();
        maintainer = new ContactInformationMaintainer(tester.controller(), Duration.ofDays(1));
    }

    @Test
    void updates_contact_information() {
        PropertyId propertyId1 = new PropertyId("1");
        PropertyId propertyId2 = new PropertyId("2");
        TenantName name1 = tester.createTenant("tenant1", "domain1", 1L);
        TenantName name2 = tester.createTenant("zenant1", "domain2", 2L);
        Supplier<AthenzTenant> tenant1 = () -> (AthenzTenant) tester.controller().tenants().require(name1);
        Supplier<AthenzTenant> tenant2 = () -> (AthenzTenant) tester.controller().tenants().require(name2);
        assertFalse(tenant1.get().contact().isPresent(), "No contact information initially");
        assertFalse(tenant2.get().contact().isPresent(), "No contact information initially");

        Contact contact = testContact();
        tester.serviceRegistry().contactRetriever().addContact(propertyId1, () -> {
            throw new RuntimeException("ERROR");
        });
        tester.serviceRegistry().contactRetriever().addContact(propertyId2, () -> contact);
        maintainer.maintain();

        assertEquals(Optional.empty(), tenant1.get().contact(), "No contact information added due to error");
        assertEquals(Optional.of(contact), tenant2.get().contact(), "Contact information added");
    }

    private static Contact testContact() {
        URI contactUrl = URI.create("http://contact1.test");
        URI issueTrackerUrl = URI.create("http://issue-tracker1.test");
        URI propertyUrl = URI.create("http://property1.test");
        List<List<String>> persons = List.of(Collections.singletonList("alice"),
                                             Collections.singletonList("bob"));
        String queue = "queue";
        Optional<String> component = Optional.empty();
        return new Contact(contactUrl, propertyUrl, issueTrackerUrl, persons, queue, component);
    }

}
