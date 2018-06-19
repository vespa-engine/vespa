// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;

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
     * Ensure ownership of the given application has been recently confirmed by the given property.
     *
     * @param issueId ID of the previous ownership issue filed for the given application.
     * @param applicationId ID of the application for which to file an issue.
     * @param propertyId ID of the property responsible for the given application.
     * @return ID of the created issue, if one was created.
     */
    Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, PropertyId propertyId);

    /**
     * Ensure ownership of the given application has been recently confirmed by the given user.
     *
     * @param issueId ID of the previous ownership issue filed for the given application.
     * @param applicationId ID of the application for which to file an issue.
     * @param owner ID of the user responsible for the given application.
     * @return ID of the created issue, if one was created.
     */
    Optional<IssueId> confirmOwnership(Optional<IssueId> issueId, ApplicationId applicationId, User owner);

    /**
     * Make sure the given ownership confirmation request is acted upon, unless it is already acknowledged.
     *
     * @param issueId ID of the ownership issue to escalate.
     * @param propertyId ID of the property responsible for the issue, if any.
     */
    void ensureResponse(IssueId issueId, Optional<PropertyId> propertyId);

}
