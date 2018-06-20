// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.google.inject.Inject;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import org.jetbrains.annotations.TestOnly;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * A memory backed implementation of the Issues API which logs changes and does nothing else.
 * 
 * @author bratseth
 * @author jonmv
 */
public class LoggingDeploymentIssues implements DeploymentIssues {

    private static final Logger log = Logger.getLogger(LoggingDeploymentIssues.class.getName());
    
    /** Whether the platform is currently broken. */
    protected final AtomicBoolean platformIssue = new AtomicBoolean(false);
    /** Last updates for each issue -- used to determine if issues are already logged and when to escalate. */
    protected final Map<IssueId, Instant> issueUpdates = new HashMap<>();

    /** Used to fabricate unique issue ids. */
    private final AtomicLong issueIdSequence = new AtomicLong(0);

    private final Clock clock;

    @SuppressWarnings("unused") // Created by dependency injection.
    @Inject
    public LoggingDeploymentIssues() {
        this(Clock.systemUTC());
    }

    @TestOnly
    protected LoggingDeploymentIssues(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IssueId fileUnlessOpen(Optional<IssueId> issueId, ApplicationId applicationId, PropertyId propertyId) {
        return fileUnlessPresent(issueId, applicationId);
    }

    @Override
    public IssueId fileUnlessOpen(Optional<IssueId> issueId, ApplicationId applicationId, User assignee) {
        return fileUnlessPresent(issueId, applicationId);
    }

    @Override
    public IssueId fileUnlessOpen(Collection<ApplicationId> applicationIds, Version version) {
        if ( ! platformIssue.get())
            log.info("These applications are all failing deployment to version " + version + ":\n" + applicationIds);

        platformIssue.set(true);
        return null;
    }

    @Override
    public void escalateIfInactive(IssueId issueId, Optional<PropertyId> propertyId, Duration maxInactivity) {
        if (issueUpdates.containsKey(issueId) && issueUpdates.get(issueId).isBefore(clock.instant().minus(maxInactivity)))
            escalateIssue(issueId);
    }

    protected void escalateIssue(IssueId issueId) {
        issueUpdates.put(issueId, clock.instant());
        log.info("Deployment issue " + issueId + " should be escalated.");
    }

    protected IssueId fileIssue(ApplicationId applicationId) {
        IssueId issueId = IssueId.from("" + issueIdSequence.incrementAndGet());
        issueUpdates.put(issueId, clock.instant());
        log.info("Deployment issue " + issueId  +": " + applicationId + " has failing deployments.");
        return issueId;
    }

    private IssueId fileUnlessPresent(Optional<IssueId> issueId, ApplicationId applicationId) {
        platformIssue.set(false);
        return issueId.filter(issueUpdates::containsKey).orElseGet(() -> fileIssue(applicationId));
    }

}
