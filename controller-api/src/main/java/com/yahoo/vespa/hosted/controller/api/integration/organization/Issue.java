// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an issue which needs to reported, typically from the controller, to a responsible party,
 * the identity of which is determined by the propertyId and, possibly, assignee fields.
 *
 * @author jvenstad
 */
public class Issue {

    private final String summary;
    private final String description;
    private final String label;
    private final User assignee;
    private final PropertyId propertyId;

    private Issue(String summary, String description, String label, User assignee, PropertyId propertyId) {
        if (summary.isEmpty()) throw new IllegalArgumentException("Issue summary can not be empty!");
        if (description.isEmpty()) throw new IllegalArgumentException("Issue description can not be empty!");
        Objects.requireNonNull(propertyId, "An issue must belong to a property!");

        this.summary = summary;
        this.description = description;
        this.label = label;
        this.assignee = assignee;
        this.propertyId = propertyId;
    }

    public Issue(String summary, String description, PropertyId propertyId) {
        this(summary, description, null, null, propertyId);
    }

    public Issue append(String appendage) {
        return new Issue(summary, description + appendage, label, assignee, propertyId);
    }

    public Issue withLabel(String label) {
        return new Issue(summary, description, label, assignee, propertyId);
    }

    public Issue withAssignee(User assignee) {
        return new Issue(summary, description, label, assignee, propertyId);
    }

    public Issue withPropertyId(PropertyId propertyId) {
        return new Issue(summary, description, label, assignee, propertyId);
    }

    public String summary() {
        return summary;
    }

    public String description() {
        return description;
    }

    public Optional<String> label() {
        return Optional.ofNullable(label);
    }

    public Optional<User> assignee() {
        return Optional.ofNullable(assignee);
    }

    public PropertyId propertyId() {
        return propertyId;
    }

}
