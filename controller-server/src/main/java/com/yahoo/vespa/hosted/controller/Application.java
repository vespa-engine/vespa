// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.application.ApplicationActivity;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.RevisionHistory;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An application. Belongs to a {@link Tenant}, and may have multiple {@link Instance}s.
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
    private final RevisionHistory revisions;
    private final OptionalLong projectId;
    private final Optional<IssueId> deploymentIssueId;
    private final Optional<IssueId> ownershipIssueId;
    private final Optional<User> owner;
    private final OptionalInt majorVersion;
    private final ApplicationMetrics metrics;
    private final Set<PublicKey> deployKeys;
    private final Map<InstanceName, Instance> instances;

    /** Creates an empty application. */
    public Application(TenantAndApplicationId id, Instant now) {
        this(id, now, DeploymentSpec.empty, ValidationOverrides.empty, Optional.empty(),
             Optional.empty(), Optional.empty(), OptionalInt.empty(), new ApplicationMetrics(0, 0),
             Set.of(), OptionalLong.empty(), RevisionHistory.empty(), List.of());
    }

    // Do not use directly - edit through LockedApplication.
    public Application(TenantAndApplicationId id, Instant createdAt, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                       Optional<IssueId> deploymentIssueId, Optional<IssueId> ownershipIssueId, Optional<User> owner,
                       OptionalInt majorVersion, ApplicationMetrics metrics, Set<PublicKey> deployKeys, OptionalLong projectId,
                       RevisionHistory revisions, Collection<Instance> instances) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "instant of creation cannot be null");
        this.deploymentSpec = Objects.requireNonNull(deploymentSpec, "deploymentSpec cannot be null");
        this.validationOverrides = Objects.requireNonNull(validationOverrides, "validationOverrides cannot be null");
        this.deploymentIssueId = Objects.requireNonNull(deploymentIssueId, "deploymentIssueId cannot be null");
        this.ownershipIssueId = Objects.requireNonNull(ownershipIssueId, "ownershipIssueId cannot be null");
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        this.majorVersion = Objects.requireNonNull(majorVersion, "majorVersion cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.deployKeys = Objects.requireNonNull(deployKeys, "deployKeys cannot be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId cannot be null");
        this.revisions = revisions;
        this.instances = instances.stream().collect(
                Collectors.collectingAndThen(Collectors.toMap(Instance::name,
                                                              Function.identity(),
                                                              (i1, i2) -> {
                                                                  throw new IllegalArgumentException("Duplicate instance " + i1.id());
                                                              },
                                                              TreeMap::new),
                                             Collections::unmodifiableMap)
        );
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

    /** Returns the known revisions for this application. */
    public RevisionHistory revisions() { return revisions; }

    /**
     * Returns the last deployed validation overrides of this application,
     * or the empty validation overrides if it has never been deployed
     * (or was deployed with an empty/missing validation overrides)
     */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    /** Returns the instances of this application */
    public Map<InstanceName, Instance> instances() { return instances; }

    /** Returns the instances of this application which are defined in its deployment spec. */
    public Map<InstanceName, Instance> productionInstances() {
        return deploymentSpec.instanceNames().stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), instances::get));
    }

    /** Returns the instance with the given name, if it exists. */
    public Optional<Instance> get(InstanceName instance) { return Optional.ofNullable(instances.get(instance)); }

    /** Returns the instance with the given name, or throws. */
    public Instance require(InstanceName instance) {
        return get(instance).orElseThrow(() -> new IllegalArgumentException("Unknown instance '" + instance + "' in '" + id + "'"));
    }

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
                                                 .toList());
    }

    public Map<InstanceName, List<Deployment>> productionDeployments() {
        return instances.values().stream()
                        .collect(Collectors.toUnmodifiableMap(Instance::name,
                                                              instance -> List.copyOf(instance.productionDeployments().values())));
    }
    /**
     * Returns the oldest platform version this has deployed in a permanent zone (not test or staging).
     *
     * This is unfortunately quite similar to {@link ApplicationController#oldestInstalledPlatform(Application)},
     * but this checks only what the controller has deployed to the production zones, while that checks the node repository
     * to see what's actually installed on each node. Thus, this is the right choice for, e.g., target Vespa versions for
     * new deployments, while that is the right choice for version to compile against.
     */
    public Optional<Version> oldestDeployedPlatform() {
        return productionDeployments().values().stream().flatMap(List::stream)
                                      .map(Deployment::version)
                                      .min(Comparator.naturalOrder());
    }

    /** Returns the oldest application version this has deployed in a permanent zone (not test or staging) */
    public Optional<RevisionId> oldestDeployedRevision() {
        return productionRevisions().min(Comparator.naturalOrder());
    }

    /** Returns the latest application version this has deployed in a permanent zone (not test or staging) */
    public Optional<RevisionId> latestDeployedRevision() {
        return productionRevisions().max(Comparator.naturalOrder());
    }

    private Stream<RevisionId> productionRevisions() {
        return productionDeployments().values().stream().flatMap(List::stream)
                                      .map(Deployment::revision)
                                      .filter(RevisionId::isProduction);
    }

    /** Returns the total quota usage for this application, excluding temporary deployments */
    public QuotaUsage quotaUsage() {
        return instances().values().stream()
                          .map(Instance::quotaUsage)
                          .reduce(QuotaUsage::add)
                          .orElse(QuotaUsage.none);
    }

    /** Returns the total quota usage for manual deployments for this application */
    public QuotaUsage manualQuotaUsage() {
        return instances().values().stream()
                          .map(Instance::manualQuotaUsage)
                          .reduce(QuotaUsage::add)
                          .orElse(QuotaUsage.none);
    }

    /** Returns the total quota usage for this application, excluding one specific deployment (and temporary deployments) */
    public QuotaUsage quotaUsage(ApplicationId application, ZoneId zone) {
        return instances().values().stream()
                          .map(instance -> instance.quotaUsageExcluding(application, zone))
                          .reduce(QuotaUsage::add)
                          .orElse(QuotaUsage.none);
    }

    /** Returns the set of deploy keys for this application. */
    public Set<PublicKey> deployKeys() { return deployKeys; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (! (o instanceof Application other)) return false;
        return id.equals(other.id);
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
