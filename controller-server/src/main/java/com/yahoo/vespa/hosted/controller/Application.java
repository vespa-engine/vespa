// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationActivity;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * An application. Belongs to a {@link Tenant}, and may have many {@link Instance}s.
 *
 * This is immutable.
 *
 * @author jonmv
 */
public class Application {

    private final TenantAndApplicationId id;
    private final Instant createdAt;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final OptionalLong projectId;
    private final boolean internal;
    private final Change change;
    private final Change outstandingChange;
    private final Optional<IssueId> deploymentIssueId;
    private final Optional<IssueId> ownershipIssueId;
    private final Optional<User> owner;
    private final OptionalInt majorVersion;
    private final ApplicationMetrics metrics;
    private final Optional<String> pemDeployKey;
    private final Map<InstanceName, Instance> instances;

    /** Creates an empty application. */
    public Application(TenantAndApplicationId id, Instant now) {
        this(id, now, DeploymentSpec.empty, ValidationOverrides.empty, Change.empty(), Change.empty(),
             Optional.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty(),
             new ApplicationMetrics(0, 0), Optional.empty(), OptionalLong.empty(), true, List.of());
    }

    // DO NOT USE! For serialization purposes, only.
    public Application(TenantAndApplicationId id, Instant createdAt, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                Change change, Change outstandingChange, Optional<IssueId> deploymentIssueId, Optional<IssueId> ownershipIssueId, Optional<User> owner,
                OptionalInt majorVersion, ApplicationMetrics metrics, Optional<String> pemDeployKey, OptionalLong projectId,
                boolean internal, Collection<Instance> instances) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "instant of creation cannot be null");
        this.deploymentSpec = Objects.requireNonNull(deploymentSpec, "deploymentSpec cannot be null");
        this.validationOverrides = Objects.requireNonNull(validationOverrides, "validationOverrides cannot be null");
        this.change = Objects.requireNonNull(change, "change cannot be null");
        this.outstandingChange = Objects.requireNonNull(outstandingChange, "outstandingChange cannot be null");
        this.deploymentIssueId = Objects.requireNonNull(deploymentIssueId, "deploymentIssueId cannot be null");
        this.ownershipIssueId = Objects.requireNonNull(ownershipIssueId, "ownershipIssueId cannot be null");
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        this.majorVersion = Objects.requireNonNull(majorVersion, "majorVersion cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.pemDeployKey = Objects.requireNonNull(pemDeployKey, "pemDeployKey cannot be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId cannot be null");
        this.internal = internal;
        this.instances = instances.stream().collect(Collectors.toUnmodifiableMap(instance -> instance.id().instance(),
                                                                                 instance -> instance));
    }

    /** Returns an aggregate application, from the given instances, if at least one. */
    public static Optional<Application> aggregate(List<Instance> instances) {
        if (instances.isEmpty())
            return Optional.empty();

        Instance base = instances.stream()
                                 .filter(instance -> instance.id().instance().isDefault())
                                 .findFirst()
                                 .orElse(instances.iterator().next());

        return Optional.of(new Application(TenantAndApplicationId.from(base.id()), base.createdAt(), base.deploymentSpec(),
                                           base.validationOverrides(), base.change(), base.outstandingChange(),
                                           base.deploymentJobs().issueId(), base.ownershipIssueId(), base.owner(),
                                           base.majorVersion(), base.metrics(), base.pemDeployKey(),
                                           base.deploymentJobs().projectId(), base.deploymentJobs().deployedInternally(), instances));
    }

    /** Returns an old Instance representation of this and the given instance, for serialisation. */
    public Instance legacy(InstanceName instance) {
        Instance base = require(instance);

        return new Instance(base.id(), createdAt, deploymentSpec, validationOverrides, base.deployments(),
                            new DeploymentJobs(projectId, base.deploymentJobs().jobStatus().values(), deploymentIssueId, internal),
                            change, outstandingChange, ownershipIssueId, owner,
                            majorVersion, metrics, pemDeployKey, base.rotations(), base.rotationStatus());
    }

    public TenantAndApplicationId id() { return id; }

    public Instant createdAt() { return createdAt; }

    /**
     * Returns the last deployed deployment spec of this application,
     * or the empty deployment spec if it has never been deployed
     */
    public DeploymentSpec deploymentSpec() { return deploymentSpec; }

    /** Returns the project id of this application, if it has any. */
    public OptionalLong projectId() { return projectId; }

    /** Returns whether this application is run on the internal deployment pipeline. */
    // TODO jonmv: Remove, as will be always true.
    public boolean internal() { return internal; }

    /**
     * Returns the last deployed validation overrides of this application,
     * or the empty validation overrides if it has never been deployed
     * (or was deployed with an empty/missing validation overrides)
     */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    /** Returns the instances of this application */
    public Map<InstanceName, Instance> instances() { return instances; }

    /** Returns the instance with the given name, if it exists. */
    public Optional<Instance> get(InstanceName instance) { return Optional.ofNullable(instances.get(instance)); }

    /** Returns the instance with the given name, or throws. */
    public Instance require(InstanceName instance) {
        return get(instance).orElseThrow(() -> new IllegalArgumentException("Unknown instance '" + instance + "'"));
    }

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

    /** Returns ID of any open deployment issue filed for this */
    public Optional<IssueId> deploymentIssueId() {
        return deploymentIssueId;
    }

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
        return ApplicationActivity.from(instances.values().stream()
                                                 .flatMap(instance -> instance.deployments().values().stream())
                                                 .collect(Collectors.toUnmodifiableList()));
    }

    public Map<InstanceName, List<Deployment>> productionDeployments() {
        return instances.values().stream()
                        .collect(Collectors.toUnmodifiableMap(Instance::name,
                                                              instance -> List.copyOf(instance.productionDeployments().values())));
    }
    /**
     * Returns the oldest platform version this has deployed in a permanent zone (not test or staging).
     *
     * This is unfortunately quite similar to {@link ApplicationController#oldestInstalledPlatform(TenantAndApplicationId)},
     * but this checks only what the controller has deployed to the production zones, while that checks the node repository
     * to see what's actually installed on each node. Thus, this is the right choice for, e.g., target Vespa versions for
     * new deployments, while that is the right choice for version to compile against.
     */
    public Optional<Version> oldestDeployedPlatform() {
        return productionDeployments().values().stream().flatMap(List::stream)
                                      .map(Deployment::version)
                                      .min(Comparator.naturalOrder());
    }

    /**
     * Returns the oldest application version this has deployed in a permanent zone (not test or staging).
     */
    public Optional<ApplicationVersion> oldestDeployedApplication() {
        return productionDeployments().values().stream().flatMap(List::stream)
                                      .map(Deployment::applicationVersion)
                                      .min(Comparator.naturalOrder());
    }

    public Optional<String> pemDeployKey() { return pemDeployKey; }

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
