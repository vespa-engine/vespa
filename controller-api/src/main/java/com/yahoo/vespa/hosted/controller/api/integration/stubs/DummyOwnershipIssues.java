// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;

import java.util.Optional;

public class DummyOwnershipIssues implements OwnershipIssues {

    @Override
    public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, PropertyId propertyId) {
        return Optional.empty();
    }

    @Override
    public Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, User owner) {
        return Optional.empty();
    }

    @Override
    public void ensureResponse(IssueId issueId, Optional<PropertyId> propertyId) {

    }

}
