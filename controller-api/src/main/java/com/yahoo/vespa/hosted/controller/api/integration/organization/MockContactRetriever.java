// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author olaa
 */
public class MockContactRetriever implements ContactRetriever{

    private final Map<PropertyId, Supplier<Contact>> contacts = new HashMap<>();

    @Override
    public Contact getContact(Optional<PropertyId> propertyId) {
        return contacts.getOrDefault(propertyId.get(), this::contact).get();
    }

    public void addContact(PropertyId propertyId, Supplier<Contact> contact) {
        contacts.put(propertyId, contact);
    }

    public void addContact(PropertyId propertyId, Contact contact) {
        contacts.put(propertyId, () -> contact);
    }

    public Contact contact() {
        return new Contact(URI.create("contacts.tld"), URI.create("properties.tld"), URI.create("issues.tld"), Collections.emptyList(), "queue", Optional.of("component"));
    }

}
