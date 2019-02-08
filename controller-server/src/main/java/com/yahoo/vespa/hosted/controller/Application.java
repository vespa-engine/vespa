// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationActivity;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.GlobalDnsName;
import com.yahoo.vespa.hosted.controller.application.RotationStatus;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An instance of an application.
 *
 * This is immutable.
 *
 * @author bratseth
 */
public class Application {

    private final ApplicationId id;
    private final Instant createdAt;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Map<ZoneId, Deployment> deployments;
    private final DeploymentJobs deploymentJobs;
    private final Change change;
    private final Change outstandingChange;
    private final Optional<IssueId> ownershipIssueId;
    private final Optional<User> owner;
    private final OptionalInt majorVersion;
    private final ApplicationMetrics metrics;
    private final Optional<RotationId> rotation;
    private final Map<HostName, RotationStatus> rotationStatus;

    /** Creates an empty application */
    public Application(ApplicationId id, Instant now) {
        this(id, now, DeploymentSpec.empty, ValidationOverrides.empty, Collections.emptyMap(),
             new DeploymentJobs(OptionalLong.empty(), Collections.emptyList(), Optional.empty(), false),
             Change.empty(), Change.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty(),
             new ApplicationMetrics(0, 0),
             Optional.empty(), Collections.emptyMap());
    }

    /** Used from persistence layer: Do not use */
    public Application(ApplicationId id, Instant createdAt, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                       List<Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                       Change outstandingChange, Optional<IssueId> ownershipIssueId, Optional<User> owner,
                       OptionalInt majorVersion, ApplicationMetrics metrics,
                       Optional<RotationId> rotation, Map<HostName, RotationStatus> rotationStatus) {
        this(id, createdAt, deploymentSpec, validationOverrides,
             deployments.stream().collect(Collectors.toMap(Deployment::zone, d -> d)),
             deploymentJobs, change, outstandingChange, ownershipIssueId, owner, majorVersion,
             metrics, rotation, rotationStatus);
    }

    Application(ApplicationId id, Instant createdAt, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                Map<ZoneId, Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                Change outstandingChange, Optional<IssueId> ownershipIssueId, Optional<User> owner,
                OptionalInt majorVersion, ApplicationMetrics metrics,
                Optional<RotationId> rotation, Map<HostName, RotationStatus> rotationStatus) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "instant of creation cannot be null");
        this.deploymentSpec = Objects.requireNonNull(deploymentSpec, "deploymentSpec cannot be null");
        this.validationOverrides = Objects.requireNonNull(validationOverrides, "validationOverrides cannot be null");
        this.deployments = ImmutableMap.copyOf(Objects.requireNonNull(deployments, "deployments cannot be null"));
        this.deploymentJobs = Objects.requireNonNull(deploymentJobs, "deploymentJobs cannot be null");
        this.change = Objects.requireNonNull(change, "change cannot be null");
        this.outstandingChange = Objects.requireNonNull(outstandingChange, "outstandingChange cannot be null");
        this.ownershipIssueId = Objects.requireNonNull(ownershipIssueId, "ownershipIssueId cannot be null");
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        this.majorVersion = Objects.requireNonNull(majorVersion, "majorVersion cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.rotation = Objects.requireNonNull(rotation, "rotation cannot be null");
        this.rotationStatus = ImmutableMap.copyOf(Objects.requireNonNull(rotationStatus, "rotationStatus cannot be null"));
    }

    public ApplicationId id() { return id; }

    public Instant createdAt() { return createdAt; }

    /**
     * Returns the last deployed deployment spec of this application,
     * or the empty deployment spec if it has never been deployed
     */
    public DeploymentSpec deploymentSpec() { return deploymentSpec; }

    /**
     * Returns the last deployed validation overrides of this application,
     * or the empty validation overrides if it has never been deployed
     * (or was deployed with an empty/missing validation overrides)
     */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    /** Returns an immutable map of the current deployments of this */
    public Map<ZoneId, Deployment> deployments() { return deployments; }

    /**
     * Returns an immutable map of the current *production* deployments of this
     * (deployments also includes manually deployed environments)
     */
    public Map<ZoneId, Deployment> productionDeployments() {
        return ImmutableMap.copyOf(deployments.values().stream()
                                           .filter(deployment -> deployment.zone().environment() == Environment.prod)
                                           .collect(Collectors.toMap(Deployment::zone, Function.identity())));
    }

    public DeploymentJobs deploymentJobs() { return deploymentJobs; }

    /**
     * Returns base change for this application, i.e., the change that is deployed outside block windows.
     * This is empty when no change is currently under deployment.
     */
    public Change change() { return change; }

    /**
     * Returns whether this has an outstanding change (in the source repository), which
     * has currently not started deploying (because a deployment is (or was) already in progress
     */
    public Change outstandingChange() { return outstandingChange; }

    /** Returns ID of the last ownership issue filed for this */
    public Optional<IssueId> ownershipIssueId() {
        return ownershipIssueId;
    }

    public Optional<User> owner() {
        return owner;
    }

    /**
     * Overrides the system major version for this application. This override takes effect if the deployment
     * spec does not specify a major version.
     */
    public OptionalInt majorVersion() { return majorVersion; }

    /** Returns metrics for this */
    public ApplicationMetrics metrics() {
        return metrics;
    }

    /** Returns activity for this */
    public ApplicationActivity activity() {
        return ApplicationActivity.from(deployments.values());
    }

    /**
     * Returns the oldest platform version this has deployed in a permanent zone (not test or staging).
     *
     * This is unfortunately quite similar to {@link ApplicationController#oldestInstalledPlatform(ApplicationId)},
     * but this checks only what the controller has deployed to the production zones, while that checks the node repository
     * to see what's actually installed on each node. Thus, this is the right choice for, e.g., target Vespa versions for
     * new deployments, while that is the right choice for version to compile against.
     */
    public Optional<Version> oldestDeployedPlatform() {
        return productionDeployments().values().stream()
                                      .map(Deployment::version)
                                      .min(Comparator.naturalOrder());
    }

    /**
     * Returns the oldest application version this has deployed in a permanent zone (not test or staging).
     */
    public Optional<ApplicationVersion> oldestDeployedApplication() {
        return productionDeployments().values().stream()
                                      .map(Deployment::applicationVersion)
                                      .min(Comparator.naturalOrder());
    }

    /** Returns the global rotation id of this, if present */
    public Optional<RotationId> rotation() {
        return rotation;
    }

    /** Returns the global rotation dns name, if present */
    public Optional<GlobalDnsName> globalDnsName(SystemName system) {
        return rotation.map(ignored -> new GlobalDnsName(id, system));
    }

    /** Returns the status of the global rotation assigned to this. Wil be empty if this does not have a global rotation. */
    public Map<HostName, RotationStatus> rotationStatus() {
        return rotationStatus;
    }

    /** Returns the global rotation status of given deployment */
    public RotationStatus rotationStatus(Deployment deployment) {
        // Rotation status only contains VIP host names, one per zone in the system. The only way to map VIP hostname to
        // this deployment, and thereby determine rotation status, is to check if VIP hostname contains the
        // deployment's environment and region.
        return rotationStatus.entrySet().stream()
                             .filter(kv -> kv.getKey().value().contains(deployment.zone().value()))
                             .map(Map.Entry::getValue)
                             .findFirst()
                             .orElse(RotationStatus.unknown);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (! (o instanceof Application)) return false;

        Application that = (Application) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

}
