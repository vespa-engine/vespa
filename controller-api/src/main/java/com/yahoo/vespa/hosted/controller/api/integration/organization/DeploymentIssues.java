// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * Represents the people responsible for keeping Vespa up and running in a given organization, etc..
 *
 * @author jonmv
 */
public interface DeploymentIssues {

    IssueId fileUnlessOpen(Optional<IssueId> issueId, ApplicationId applicationId, AccountId assigneeId, User assignee, Contact contact);

    IssueId fileUnlessOpen(Collection<ApplicationId> applicationIds, Version version);

    void escalateIfInactive(IssueId issueId, Duration maxInactivity, Optional<Contact> contact);

}
