// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueInfo.Status;

import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author jvenstad
 */
public class MockIssueHandler implements IssueHandler {

    private final Clock clock;
    private final AtomicLong counter = new AtomicLong();
    private final Map<IssueId, MockIssue> issues = new HashMap<>();
    private final Map<IssueId, Map<String, InputStream>> attachments = new HashMap<>();
    private final Map<String, ProjectInfo> projects = new HashMap<>();

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
    public List<IssueInfo> findAllBySimilarity(Issue issue) {
        return issues.entrySet().stream()
                     .filter(entry -> entry.getValue().issue.summary().equals(issue.summary()))
                     .map(entry -> new IssueInfo(entry.getKey(),
                                                 entry.getValue().updated,
                                                 entry.getValue().isOpen() ? Status.toDo : Status.done,
                                                 entry.getValue().assignee))
                     .collect(Collectors.toList());
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
    public boolean addWatcher(IssueId issueId, String watcher) {
        issues.get(issueId).addWatcher(watcher);
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

    @Override
    public ProjectInfo projectInfo(String projectKey) {
        return projects.get(projectKey);
    }

    @Override
    public void addAttachment(IssueId id, String filename, Supplier<InputStream> contentAsStream) {
        attachments.computeIfAbsent(id, __ -> new HashMap<>()).put(filename, contentAsStream.get());
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

    public void addProject(String projectKey, ProjectInfo projectInfo) {
        projects.put(projectKey, projectInfo);
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
        private List<String> watchers;

        private MockIssue(Issue issue) {
            this.issue = issue;
            this.updated = clock.instant();
            this.open = true;
            this.assignee = issue.assignee().orElse(null);
            this.watchers = new ArrayList<>();
        }

        public Issue issue() { return issue; }
        public User assignee() { return assignee; }
        public boolean isOpen() { return open; }
        public List<String> watchers() { return List.copyOf(watchers); }
        public void addWatcher(String watcher) { watchers.add(watcher); }

    }

}
