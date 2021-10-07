// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final String queue;
    private final Optional<String> component;

    public Contact(URI url, URI propertyUrl, URI issueTrackerUrl, List<List<String>> persons, String queue, Optional<String> component) {
        this.propertyUrl = Objects.requireNonNull(propertyUrl, "propertyUrl must be non-null");
        this.url = Objects.requireNonNull(url, "url must be non-null");
        this.issueTrackerUrl = Objects.requireNonNull(issueTrackerUrl, "issueTrackerUrl must be non-null");
        this.persons = ImmutableList.copyOf(Objects.requireNonNull(persons, "persons must be non-null"));
        this.queue = queue;
        this.component = component;
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

    public String queue() {
        return queue;
    }

    public Optional<String> component() {
        return component;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(url, contact.url) &&
               Objects.equals(propertyUrl, contact.propertyUrl) &&
               Objects.equals(issueTrackerUrl, contact.issueTrackerUrl) &&
               Objects.equals(persons, contact.persons) &&
               Objects.equals(queue, contact.queue) &&
               Objects.equals(component, contact.component);
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
               ", queue=" + queue +
                (component.isPresent() ? ", component=" + component.get() : "") +
               '}';
    }

}
