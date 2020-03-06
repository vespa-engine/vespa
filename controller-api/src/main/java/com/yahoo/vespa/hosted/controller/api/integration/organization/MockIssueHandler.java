// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.inject.Inject;

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
import java.util.stream.Collectors;

/**
 * @author jvenstad
 */
public class MockIssueHandler implements IssueHandler {

    private final Clock clock;
    private final AtomicLong counter = new AtomicLong();
    private final Map<IssueId, MockIssue> issues = new HashMap<>();

    @Inject
    @SuppressWarnings("unused")
    public MockIssueHandler() {
        this(Clock.systemUTC());
    }

    public MockIssueHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IssueId file(Issue issue) {
        if (!issue.assignee().isPresent()) throw new RuntimeException();
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
    public Optional<User> escalate(IssueId issueId, Contact contact) {
        List<List<User>> contacts = getContactUsers(contact);
        Optional<User> assignee = assigneeOf(issueId);
        int assigneeLevel = -1;
        if (assignee.isPresent())
            for (int level = contacts.size(); --level > assigneeLevel; )
                if (contacts.get(level).contains(assignee.get()))
                    assigneeLevel = level;

        for (int level = assigneeLevel + 1; level < contacts.size(); level++)
            for (User target : contacts.get(level))
                if (reassign(issueId, target))
                    return Optional.of(target);

        return Optional.empty();
    }

    @Override
    public boolean issueExists(Issue issue) {
        return issues.values().stream().anyMatch(i -> i.issue.summary().equals(issue.summary()));
    }

    public MockIssueHandler close(IssueId issueId) {
        issues.get(issueId).open = false;
        touch(issueId);
        return this;
    }

    public Map<IssueId, MockIssue> issues() {
        return issues;
    }

    private List<List<User>> getContactUsers(Contact contact) {
        return contact.persons().stream()
                .map(userList ->
                        userList.stream().map(user ->
                                user.split(" ")[0])
                                .map(User::from)
                                .collect(Collectors.toList())
                ).collect(Collectors.toList());
    }


    private void touch(IssueId issueId) {
        issues.get(issueId).updated = clock.instant();
    }

    private static class PropertyInfo {

        private List<List<User>> contacts = Collections.emptyList();
        private URI issueUrl = URI.create("issues.tld");
        private URI contactsUrl = URI.create("contacts.tld");
        private URI propertyUrl = URI.create("properties.tld");

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
            this.assignee = issue.assignee().orElse(null);
        }

        public Issue issue() { return issue; }
        public User assignee() { return assignee; }
        public boolean isOpen() { return open; }

    }

}
