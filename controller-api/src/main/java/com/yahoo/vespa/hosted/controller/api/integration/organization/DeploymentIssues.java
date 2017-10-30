// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

/**
 * Represents the people responsible for keeping Vespa up and running in a given organization, etc..
 *
 * @author jvenstad
 */
public interface DeploymentIssues {

    IssueId fileUnlessOpen(Optional<IssueId> issueId, ApplicationId applicationId, PropertyId propertyId);

    IssueId fileUnlessOpen(Optional<IssueId> issueId, ApplicationId applicationId, User assignee);

    IssueId fileUnlessOpen(Collection<ApplicationId> applicationIds, Version version);

    void escalateIfInactive(IssueId issueId, Optional<PropertyId> propertyId, Duration maxInactivity);

}
