// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationRotation;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Map<ZoneId, Deployment> deployments;
    private final DeploymentJobs deploymentJobs;
    private final Change change;
    private final Change outstandingChange;
    private final Optional<IssueId> ownershipIssueId;
    private final ApplicationMetrics metrics;
    private final Optional<RotationId> rotation;

    /** Creates an empty application */
    public Application(ApplicationId id) {
        this(id, DeploymentSpec.empty, ValidationOverrides.empty, Collections.emptyMap(),
             new DeploymentJobs(Optional.empty(), Collections.emptyList(), Optional.empty()),
             Change.empty(), Change.empty(), Optional.empty(), new ApplicationMetrics(0, 0),
             Optional.empty());
    }

    /** Used from persistence layer: Do not use */
    public Application(ApplicationId id, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                       List<Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                       Change outstandingChange, Optional<IssueId> ownershipIssueId, ApplicationMetrics metrics,
                       Optional<RotationId> rotation) {
        this(id, deploymentSpec, validationOverrides,
             deployments.stream().collect(Collectors.toMap(Deployment::zone, d -> d)),
             deploymentJobs, change, outstandingChange, ownershipIssueId, metrics, rotation);
    }

    Application(ApplicationId id, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                Map<ZoneId, Deployment> deployments, DeploymentJobs deploymentJobs, Change change,
                Change outstandingChange, Optional<IssueId> ownershipIssueId, ApplicationMetrics metrics,
                Optional<RotationId> rotation) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(deploymentSpec, "deploymentSpec cannot be null");
        Objects.requireNonNull(validationOverrides, "validationOverrides cannot be null");
        Objects.requireNonNull(deployments, "deployments cannot be null");
        Objects.requireNonNull(deploymentJobs, "deploymentJobs cannot be null");
        Objects.requireNonNull(change, "change cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(rotation, "rotation cannot be null");
        this.id = id;
        this.deploymentSpec = deploymentSpec;
        this.validationOverrides = validationOverrides;
        this.deployments = ImmutableMap.copyOf(deployments);
        this.deploymentJobs = deploymentJobs;
        this.change = change;
        this.outstandingChange = outstandingChange;
        this.ownershipIssueId = ownershipIssueId;
        this.metrics = metrics;
        this.rotation = rotation;
    }

    public ApplicationId id() { return id; }

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
     * Returns the change that should currently be deployed for this application,
     * which is empty when no change is in progress.
     */
    public Change change() { return change; }

    /**
     * Returns whether this has an outstanding change (in the source repository), which
     * has currently not started deploying (because a deployment is (or was) already in progress
     */
    public Change outstandingChange() { return outstandingChange; }

    public Optional<IssueId> ownershipIssueId() {
        return ownershipIssueId;
    }

    public ApplicationMetrics metrics() {
        return metrics;
    }

    /**
     * Returns the oldest version this has deployed in a permanent zone (not test or staging),
     * or empty version if it is not deployed anywhere
     */
    public Optional<Version> oldestDeployedVersion() {
        return productionDeployments().values().stream()
                .map(Deployment::version)
                .min(Comparator.naturalOrder());
    }

    /** Returns the version a new deployment to this zone should use for this application */
    public Version deployVersionIn(ZoneId zone, Controller controller) {
        return change.platform().orElse(versionIn(zone, controller));
    }

    /** Returns the current version this application has, or if none; should use, in the given zone */
    public Version versionIn(ZoneId zone, Controller controller) {
        return Optional.ofNullable(deployments().get(zone)).map(Deployment::version) // Already deployed in this zone: Use that version
                       .orElse(oldestDeployedVersion().orElse(controller.systemVersion()));
    }

    /** Returns the Vespa version to use for the given job */
    public Version deployVersionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == DeploymentJobs.JobType.component
                ? controller.systemVersion()
                : deployVersionIn(jobType.zone(controller.system()).get(), controller);
    }

    /** Returns the application version to use for the given job */
    public Optional<ApplicationVersion> deployApplicationVersionFor(DeploymentJobs.JobType jobType,
                                                                    Controller controller,
                                                                    boolean currentVersion) {
        // Use last successful version if currentVersion is requested (staging deployment) or when we're not deploying
        // a new application version
        if (currentVersion || !change().application().isPresent()) {
            Optional<ApplicationVersion> version = deploymentJobs().lastSuccessfulApplicationVersionFor(jobType);
            if (version.isPresent()) {
                return version;
            }
        }
        return jobType == DeploymentJobs.JobType.component
                ? Optional.empty()
                : deployApplicationVersionIn(jobType.zone(controller.system()).get());
    }

    /** Returns the global rotation of this, if present */
    public Optional<ApplicationRotation> rotation() {
        return rotation.map(rotation -> new ApplicationRotation(id, rotation));
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

    /** Returns whether changes to this are blocked in the given instant */
    public boolean isBlocked(Instant instant) {
         return ! deploymentSpec().canUpgradeAt(instant) || ! deploymentSpec().canChangeRevisionAt(instant);
    }

    /** Returns the application version a deployment to this zone should use, or empty if we don't know */
    private Optional<ApplicationVersion> deployApplicationVersionIn(ZoneId zone) {
        if (change().application().isPresent()) {
            return Optional.of(change().application().get());
        }
        return applicationVersionIn(zone);
    }

    /** Returns the application version that is or should be deployed with in the given zone, or empty if unknown. */
    private Optional<ApplicationVersion> applicationVersionIn(ZoneId zone) {
        return Optional.ofNullable(deployments().get(zone)).map(Deployment::applicationVersion);
    }

}
