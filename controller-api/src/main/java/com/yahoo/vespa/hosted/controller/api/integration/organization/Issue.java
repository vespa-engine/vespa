// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.util.Optional;

public class Issue {

    private final String summary;
    private final String description;
    private final User assignee;
    private final PropertyId propertyId;

    private Issue(String summary, String description, User assignee, PropertyId propertyId) {
        this.summary = summary;
        this.description = description;
        this.assignee = assignee;
        this.propertyId = propertyId;
    }

    public Issue(String summary, String description) {
        this(summary, description, null, null);
    }

    public Issue append(String appendage) {
        return new Issue(summary, description + appendage, assignee, propertyId);
    }

    public Issue withUser(User assignee) {
        return new Issue(summary, description, assignee, propertyId);
    }

    public Issue withPropertyId(PropertyId propertyId) {
        return new Issue(summary, description, assignee, propertyId);
    }

    public String summary() {
        return summary;
    }

    public String description() {
        return description;
    }

    public Optional<User> assignee() {
        return Optional.ofNullable(assignee);
    }

    public Optional<PropertyId> propertyId() {
        return Optional.ofNullable(propertyId);
    }

}
