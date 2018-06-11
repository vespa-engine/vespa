// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.inject.Inject;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jvenstad
 */
public class MockOrganization implements Organization {

    private final Clock clock;
    private final AtomicLong counter = new AtomicLong();
    private final Map<IssueId, MockIssue> issues = new HashMap<>();
    private final Map<PropertyId, PropertyInfo> properties = new HashMap<>();

    @Inject
    @SuppressWarnings("unused")
    public MockOrganization() {
        this(Clock.systemUTC());
    }

    public MockOrganization(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IssueId file(Issue issue) {
        IssueId issueId = IssueId.from("" + counter.incrementAndGet());
        issues.put(issueId, new MockIssue(issue));
        return issueId;
    }

    @Override
    public Optional<IssueId> findBySimilarity(Issue issue) {
        return issues.entrySet().stream()
                     .filter(entry -> entry.getValue().issue.summary().equals(issue.summary()))
                     .findFirst()
                     .map(Map.Entry::getKey);
    }

    @Override
    public void update(IssueId issueId, String description) {
        touch(issueId);
    }

    @Override
    public void commentOn(IssueId issueId, String comment) {
        touch(issueId);
    }

    @Override
    public boolean isOpen(IssueId issueId) {
        return issues.get(issueId).open;
    }

    @Override
    public boolean isActive(IssueId issueId, Duration maxInactivity) {
        return issues.get(issueId).updated.isAfter(clock.instant().minus(maxInactivity));
    }

    @Override
    public Optional<User> assigneeOf(IssueId issueId) {
        return Optional.ofNullable(issues.get(issueId).assignee);
    }

    @Override
    public boolean reassign(IssueId issueId, User assignee) {
        issues.get(issueId).assignee = assignee;
        touch(issueId);
        return true;
    }

    @Override
    public List<? extends List<? extends User>> contactsFor(PropertyId propertyId) {
        return properties.getOrDefault(propertyId, new PropertyInfo()).contacts;
    }

    @Override
    public URI issueCreationUri(PropertyId propertyId) {
        return URI.create("www.issues.tld/" + propertyId.id());
    }

    @Override
    public URI contactsUri(PropertyId propertyId) {
        return URI.create("www.contacts.tld/" + propertyId.id());
    }

    @Override
    public URI propertyUri(PropertyId propertyId) {
        return URI.create("www.properties.tld/" + propertyId.id());
    }

    public Map<IssueId, MockIssue> issues() {
        return Collections.unmodifiableMap(issues);
    }

    public void close(IssueId issueId) {
        issues.get(issueId).open = false;
        touch(issueId);
    }

    public void setDefaultAssigneeFor(PropertyId propertyId, User defaultAssignee) {
        properties.get(propertyId).defaultAssignee = defaultAssignee;
    }

    public void setContactsFor(PropertyId propertyId, List<List<User>> contacts) {
        properties.get(propertyId).contacts = contacts;
    }

    public void addProperty(PropertyId propertyId) {
        properties.put(propertyId, new PropertyInfo());
    }

    private void touch(IssueId issueId) {
        issues.get(issueId).updated = clock.instant();
    }


    public class MockIssue {

        private Issue issue;
        private Instant updated;
        private boolean open;
        private User assignee;

        private MockIssue(Issue issue) {
            this.issue = issue;
            this.updated = clock.instant();
            this.open = true;
            this.assignee = issue.assignee().orElse(properties.get(issue.propertyId()).defaultAssignee);
        }

        public Issue issue() { return issue; }
        public User assignee() { return assignee; }
        public boolean isOpen() { return open; }

    }


    private class PropertyInfo {

        private User defaultAssignee;
        private List<List<User>> contacts = Collections.emptyList();

    }

}

