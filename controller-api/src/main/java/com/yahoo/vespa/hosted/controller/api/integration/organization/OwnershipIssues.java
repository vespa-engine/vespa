// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import java.util.Optional;

/**
 * Periodically issues ownership confirmation requests for given applications, and escalates the issues if needed.
 *
 * Even machines wrought from cold steel occasionally require the gentle touch only a fleshling can provide.
 * By making humans regularly acknowledge their dedication to given applications, this class provides the machine
 * with reassurance that any misbehaving applications will swiftly be dealt with.
 * Ignored confirmation requests are periodically redirected to humans of higher rank, until they are acknowledged.
 *
 * @author jonmv
 */
public interface OwnershipIssues {

    /**
     * Ensure ownership of the given application has been recently confirmed by the given user.
     *
     * @param issueId ID of the previous ownership issue filed for the given application.
     * @param summary Summary of an application for which to file an issue.
     * @param assigneeId Issue assignee id
     * @param assignee Issue assignee
     * @param contact Contact info for the application tenant
     * @return ID of the created issue, if one was created.
     */
    Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationSummary summary, AccountId assigneeId, User assignee, Contact contact);

    /**
     * Make sure the given ownership confirmation request is acted upon, unless it is already acknowledged.
     * @param issueId ID of the ownership issue to escalate.
     * @param contact Contact information of application tenant
     */
    void ensureResponse(IssueId issueId, Optional<Contact> contact);

    /**
     * Get the owner of an application, given its ownership issue ID.
     * @param issueId ID of the ownership issue.
     * @return The owner of the application, if it has been confirmed.
     */
    Optional<AccountId> getConfirmedOwner(IssueId issueId);

}
