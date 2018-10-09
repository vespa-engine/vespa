// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.google.common.collect.ImmutableList;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Contact information for a tenant.
 *
 * @author mpolden
 */
public class Contact {

    private final URI url;
    private final URI propertyUrl;
    private final URI issueTrackerUrl;
    private final List<List<String>> persons;

    public Contact(URI url, URI propertyUrl, URI issueTrackerUrl, List<List<String>> persons) {
        this.propertyUrl = Objects.requireNonNull(propertyUrl, "propertyUrl must be non-null");
        this.url = Objects.requireNonNull(url, "url must be non-null");
        this.issueTrackerUrl = Objects.requireNonNull(issueTrackerUrl, "issueTrackerUrl must be non-null");
        this.persons = ImmutableList.copyOf(Objects.requireNonNull(persons, "persons must be non-null"));
    }

    /** URL to this */
    public URI url() {
        return url;
    }

    /** URL to information about this property */
    public URI propertyUrl() {
        return propertyUrl;
    }

    /** URL to this contacts's issue tracker */
    public URI issueTrackerUrl() {
        return issueTrackerUrl;
    }

    /** Nested list of persons representing this. First level represents that person's rank in the corporate dystopia. */
    public List<List<String>> persons() {
        return persons;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(url, contact.url) &&
               Objects.equals(propertyUrl, contact.propertyUrl) &&
               Objects.equals(issueTrackerUrl, contact.issueTrackerUrl) &&
               Objects.equals(persons, contact.persons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, propertyUrl, issueTrackerUrl, persons);
    }

    @Override
    public String toString() {
        return "Contact{" +
               "url=" + url +
               ", propertyUrl=" + propertyUrl +
               ", issueTrackerUrl=" + issueTrackerUrl +
               ", persons=" + persons +
               '}';
    }

}
