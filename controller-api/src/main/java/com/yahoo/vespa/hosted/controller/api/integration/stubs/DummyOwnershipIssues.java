// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.organization.AccountId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ApplicationSummary;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;

import java.util.Optional;

/**
 * @author jonmv
 */
public class DummyOwnershipIssues implements OwnershipIssues {

    @Override
    public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationSummary summary, AccountId assigneeId, User assignee, Contact contact) {
        return Optional.empty();
    }

    @Override
    public void ensureResponse(IssueId issueId, Optional<Contact> contact) {
    }

    @Override
    public Optional<AccountId> getConfirmedOwner(IssueId issueId) {
        return Optional.empty();
    }

}
