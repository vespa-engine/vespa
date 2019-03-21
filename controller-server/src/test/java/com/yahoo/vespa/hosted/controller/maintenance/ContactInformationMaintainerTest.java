// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class ContactInformationMaintainerTest {

    private ControllerTester tester;
    private ContactInformationMaintainer maintainer;

    @Before
    public void before() {
        tester = new ControllerTester();
        maintainer = new ContactInformationMaintainer(tester.controller(), Duration.ofDays(1),
                                                      new JobControl(tester.controller().curator()),
                                                      tester.contactRetriever());
    }

    @Test
    public void updates_contact_information() {
        long propertyId = 1;
        TenantName name = tester.createTenant("tenant1", "domain1", propertyId);
        Supplier<AthenzTenant> tenant = () -> (AthenzTenant) tester.controller().tenants().require(name);
        assertFalse("No contact information initially", tenant.get().contact().isPresent());

        Contact contact = testContact();
        registerContact(propertyId, contact);
        maintainer.run();

        assertTrue("Contact information added", tenant.get().contact().isPresent());
        assertEquals(contact, tenant.get().contact().get());
    }

    private void registerContact(long propertyId, Contact contact) {
        PropertyId p = new PropertyId(String.valueOf(propertyId));
        tester.contactRetriever().addContact(p, contact);
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
