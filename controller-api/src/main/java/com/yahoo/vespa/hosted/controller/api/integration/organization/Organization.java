package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public interface Organization {

    /**
     * Returns a flat list of all escalation targets among the given users.
     * An escalation target is anyone of higher rank than the given assignee.
     */
    static List<User> escalationTargetsFrom(List<List<User>> contacts, User assignee) {
        for (int i = 0; i < contacts.size(); i++)
            if (contacts.get(i).contains(assignee))
                return contacts.subList(i + 1, contacts.size()).stream().flatMap(List::stream).collect(Collectors.toList());

        return contacts.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    /**
     * File an issue with its given property or the default, and with the specific assignee, if present.
     *
     * @param issue The issue to file.
     * @return ID of the created issue.
     */
    IssueId file(Issue issue);

    /**
     * File the given issue, or update it if is already exists (based on similarity).
     *
     * @param issue The issue to file or update.
     * @return ID of the created or updated issue.
     */
    IssueId fileOrUpdate(Issue issue);

    /**
     * Reassign an issue to the Vespa operations team for termination.
     *
     * @param issueId ID of the issue to reassign.
     */
    void terminate(IssueId issueId);

    /**
     * Escalate an issue filed with the given property.
     *
     * @param issueId ID of the issue to escalate.
     * @param propertyId PropertyId of the tenant owning the application for which the issue was filed.
     */
    default void escalate(IssueId issueId, PropertyId propertyId) {
        for (User target : escalationTargetsFrom(contactsFor(propertyId), assigneeOf(issueId)))
            if (reassign(issueId, target))
                break;
    }

    /**
     * Returns the user assigned to the given issue, if any.
     *
     * @param issueId ID of the issue for which to find the assignee.
     * @return The user responsible for fixing the given issue, if found.
     */
    User assigneeOf(IssueId issueId);

    /**
     * Add a comment to the issue with the given ID.
     *
     * @param issueId ID of the issue to comment on.
     * @param comment The comment to add.
     */
    void comment(IssueId issueId, String comment);

    /**
     * Reassign the issue with the given ID to the given user, and returns the outcome of this.
     *
     * @param issueId ID of the issue to be reassigned.
     * @param assignee User to which the issue shall be assigned.
     * @return Whether the reassignment was successful.
     */
    boolean reassign(IssueId issueId, User assignee);

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
    boolean isActive(IssueId issueId, Duration maxInactivityAge);

    /**
     * Returns a nested list where the entries have increasing rank, and where each entry is
     * a list of the users of that rank, by decreasing relevance.
     *
     * @param propertyId ID of the property for which to list contacts.
     * @return A sorted, nested, sorted list of contacts.
     */
    List<List<User>> contactsFor(PropertyId propertyId);

}
