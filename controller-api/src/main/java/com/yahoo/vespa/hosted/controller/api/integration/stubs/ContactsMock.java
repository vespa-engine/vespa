// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.Contacts;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mpolden
 */
public class ContactsMock implements Contacts {

    private final Map<Long, List<UserContact>> userContacts = new HashMap<>();

    public void addContact(long propertyId, List<UserContact> contacts) {
        userContacts.put(propertyId, contacts);
    }

    public List<UserContact> userContactsFor(long propertyId) {
        return userContacts.get(propertyId);
    }

    @Override
    public URI contactsUri(long propertyId) {
        return URI.create("http://contacts.test?propertyId=" + propertyId);
    }

}
