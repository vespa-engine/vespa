// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Represents the humans who use this software, and their organization.
 * Lets the software report issues to its caretakers, and provides other useful human resource lookups.
 *
 * @author jonmv
 */
public interface Organization {

    /**
     * File an issue with its given property or the default, and with the specific assignee, if present.
     *
     * @param issue The issue to file.
     * @return ID of the created issue.
     */
    IssueId file(Issue issue);

    /**
     * Returns the ID of this issue, if it exists and is open, based on a similarity search.
     *
     * @param issue The issue to search for; relevant fields are the summary and the owner (propertyId).
     * @return ID of the issue, if it is found.
     */
    Optional<IssueId> findBySimilarity(Issue issue);

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
     * Escalate an issue filed with the given property.
     *
     * @param issueId ID of the issue to escalate.
     * @param propertyId PropertyId of the tenant owning the application for which the issue was filed.
     * @return User that was assigned issue as a result of the escalation, if any
     */
    default Optional<User> escalate(IssueId issueId, PropertyId propertyId) {
        List<? extends List<? extends User>> contacts = contactsFor(propertyId);

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

    /**
     * Returns a nested list where the entries have increasing rank, and where each entry is
     * a list of the users of that rank, by decreasing relevance.
     *
     * @param propertyId ID of the property for which to list contacts.
     * @return A sorted, nested, reverse sorted list of contacts.
     */
    List<? extends List<? extends User>> contactsFor(PropertyId propertyId);

    URI issueCreationUri(PropertyId propertyId);

    URI contactsUri(PropertyId propertyId);

    URI propertyUri(PropertyId propertyId);

}
