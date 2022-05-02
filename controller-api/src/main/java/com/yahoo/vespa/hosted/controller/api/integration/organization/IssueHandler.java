// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;


import com.yahoo.vespa.hosted.controller.api.integration.jira.JiraIssue;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author jonmv
 */
public interface IssueHandler {

    /**
     * File an issue with its given property or the default, and with the specific assignee, if present.
     *
     * @param issue The issue to file.
     * @return ID of the created issue.
     */
    IssueId file(Issue issue);

    /**
     * Returns all open issues similar to the given.
     *
     * @param issue The issue to search for; relevant fields are the summary and the owner (propertyId).
     * @return All open, similar issues.
     */
    List<IssueInfo> findAllBySimilarity(Issue issue);

    /**
     * Returns the ID of this issue, if it exists and is open, based on a similarity search.
     *
     * @param issue The issue to search for; relevant fields are the summary and the owner (propertyId).
     * @return ID of the issue, if it is found.
     */
    default Optional<IssueId> findBySimilarity(Issue issue) {
        return findAllBySimilarity(issue).stream().findFirst().map(IssueInfo::id);
    }

    /**
     * Update the description of the issue with the given ID.
     *
     * @param issueId ID of the issue to comment on.
     * @param description The updated description.
     */
    void update(IssueId issueId, String description);

    /**
     * Add a comment to the issue with the given ID.
     *
     * @param issueId ID of the issue to comment on.
     * @param comment The comment to add.
     */
    void commentOn(IssueId issueId, String comment);

    /**
     * Returns whether the issue is still under investigation.
     *
     * @param issueId ID of the issue to examine.
     * @return Whether the given issue is under investigation.
     */
    boolean isOpen(IssueId issueId);

    /**
     * Returns whether there has been significant activity on the issue within the given duration.
     *
     * @param issueId ID of the issue to examine.
     * @return Whether the given issue is actively worked on.
     */
    boolean isActive(IssueId issueId, Duration maxInactivity);

    /**
     * Returns the user assigned to the given issue, if any.
     *
     * @param issueId ID of the issue for which to find the assignee.
     * @return The user responsible for fixing the given issue, if found.
     */
    Optional<User> assigneeOf(IssueId issueId);

    /**
     * Reassign the issue with the given ID to the given user, and returns the outcome of this.
     *
     * @param issueId ID of the issue to be reassigned.
     * @param assignee User to which the issue shall be assigned.
     * @return Whether the reassignment was successful.
     */
    boolean reassign(IssueId issueId, User assignee);

    /**
     * Reassign the issue with the given ID to the given user, and returns the outcome of this.
     *
     * @param issueId ID of the issue to be watched.
     * @param watcher watcher to add to the issue.
     * @return Whether adding the watcher was successful.
     */
    boolean addWatcher(IssueId issueId, String watcher);

    /**
     * Escalate an issue filed with the given property.
     *
     * @param issueId ID of the issue to escalate.
     * @return User that was assigned issue as a result of the escalation, if any
     */
    Optional<User> escalate(IssueId issueId, Contact contact);

    /**
     * Returns whether there exists an issue with an exactly matching summary.
     *
     * @param issue The summary of the issue.
     * @return Whether the issue exists.
     */
    boolean issueExists(Issue issue);

    /**
     * Returns information about project identified by the project key
     *
     * @param projectKey The project key to find information for
     * @return Project info for project
     * @throws RuntimeException exception if project not found
     */
    ProjectInfo projectInfo(String projectKey);

    /** Upload an attachment to the issue, with indicated filename, from the given input stream. */
    void addAttachment(IssueId id, String filename, Supplier<InputStream> contentAsStream);

}
