// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.zone.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireAtLeast;
import static ai.vespa.validation.Validation.requireInRange;
import static com.yahoo.config.application.api.DeploymentSpec.RevisionChange.whenClear;
import static com.yahoo.config.application.api.DeploymentSpec.RevisionTarget.next;
import static com.yahoo.config.provision.Environment.prod;

/**
 * The deployment spec for an application instance
 *
 * @author bratseth
 */
public class DeploymentInstanceSpec extends DeploymentSpec.Steps {

    /** The maximum number of consecutive days Vespa upgrades are allowed to be blocked */
    private static final int maxUpgradeBlockingDays = 21;

    /** The name of the instance this step deploys */
    private final InstanceName name;

    private final Tags tags;
    private final DeploymentSpec.UpgradePolicy upgradePolicy;
    private final DeploymentSpec.RevisionTarget revisionTarget;
    private final DeploymentSpec.RevisionChange revisionChange;
    private final DeploymentSpec.UpgradeRollout upgradeRollout;
    private final int minRisk;
    private final int maxRisk;
    private final int maxIdleHours;
    private final List<DeploymentSpec.ChangeBlocker> changeBlockers;
    private final Optional<String> globalServiceId;
    private final Optional<AthenzService> athenzService;
    private final Optional<CloudAccount> cloudAccount;
    private final Notifications notifications;
    private final List<Endpoint> endpoints;
    private final Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints;
    private final Bcp bcp;

    public DeploymentInstanceSpec(InstanceName name,
                                  Tags tags,
                                  List<DeploymentSpec.Step> steps,
                                  DeploymentSpec.UpgradePolicy upgradePolicy,
                                  DeploymentSpec.RevisionTarget revisionTarget,
                                  DeploymentSpec.RevisionChange revisionChange,
                                  DeploymentSpec.UpgradeRollout upgradeRollout,
                                  int minRisk, int maxRisk, int maxIdleHours,
                                  List<DeploymentSpec.ChangeBlocker> changeBlockers,
                                  Optional<String> globalServiceId,
                                  Optional<AthenzService> athenzService,
                                  Optional<CloudAccount> cloudAccount,
                                  Notifications notifications,
                                  List<Endpoint> endpoints,
                                  Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints,
                                  Bcp bcp,
                                  Instant now) {
        super(steps);
        this.name = Objects.requireNonNull(name);
        this.tags = Objects.requireNonNull(tags);
        this.upgradePolicy = Objects.requireNonNull(upgradePolicy);
        Objects.requireNonNull(revisionTarget);
        Objects.requireNonNull(revisionChange);
        this.revisionTarget = require(maxRisk == 0 || revisionTarget == next, revisionTarget,
                                      "revision-target must be 'next' when max-risk is specified");
        this.revisionChange = require(maxRisk == 0 || revisionChange == whenClear, revisionChange,
                                      "revision-change must be 'when-clear' when max-risk is specified");
        this.upgradeRollout = Objects.requireNonNull(upgradeRollout);
        this.minRisk = requireAtLeast(minRisk, "minimum risk score", 0);
        this.maxRisk = require(maxRisk >= minRisk, maxRisk, "maximum risk cannot be less than minimum risk score");
        this.maxIdleHours = requireInRange(maxIdleHours, "maximum idle hours", 0, 168);
        this.changeBlockers = Objects.requireNonNull(changeBlockers);
        this.globalServiceId = Objects.requireNonNull(globalServiceId);
        this.athenzService = Objects.requireNonNull(athenzService);
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.notifications = Objects.requireNonNull(notifications);
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints));
        Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpointsCopy =  new HashMap<>();
        for (var entry : zoneEndpoints.entrySet()) zoneEndpointsCopy.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
        this.zoneEndpoints = Collections.unmodifiableMap(zoneEndpointsCopy);
        this.bcp = Objects.requireNonNull(bcp);
        validateZones(new HashSet<>(), new HashSet<>(), this);
        validateEndpoints(globalServiceId, this.endpoints);
        validateChangeBlockers(changeBlockers, now);
        validateBcp(bcp);
    }

    public InstanceName name() { return name; }

    public Tags tags() { return tags; }

    /**
     * Throws an IllegalArgumentException if any production deployment or test is declared multiple times,
     * or if any production test is declared not after its corresponding deployment.
     *
     * @param deployments previously seen deployments
     * @param tests previously seen tests
     * @param step step whose members to validate
     */
    private static void validateZones(Set<RegionName> deployments, Set<RegionName> tests, DeploymentSpec.Step step) {
        if ( ! step.steps().isEmpty()) {
            Set<RegionName> oldDeployments = Set.copyOf(deployments);
            for (DeploymentSpec.Step nested : step.steps()) {
                Set<RegionName> seenDeployments = new HashSet<>(step.isOrdered() ? deployments : oldDeployments);
                validateZones(seenDeployments, tests, nested);
                deployments.addAll(seenDeployments);
            }
        }
        else if (step.concerns(Environment.prod)) {
            if (step.isTest()) {
                RegionName region = ((DeploymentSpec.DeclaredTest) step).region();
                if ( ! deployments.contains(region))
                    throw new IllegalArgumentException("tests for prod." + region + " must be after the corresponding deployment in deployment.xml");
                if ( ! tests.add(region))
                    throw new IllegalArgumentException("tests for prod." + region + " are listed twice in deployment.xml");
            }
            else {
                RegionName region = ((DeploymentSpec.DeclaredZone) step).region().get();
                if ( ! deployments.add(region))
                    throw new IllegalArgumentException("prod." + region + " is listed twice in deployment.xml");
            }
        }
    }

    /** Throw an IllegalArgumentException if an endpoint refers to a region that is not declared in 'prod' */
    private void validateEndpoints(Optional<String> globalServiceId, List<Endpoint> endpoints) {
        if (globalServiceId.isPresent() && ! endpoints.isEmpty()) {
            throw new IllegalArgumentException("Providing both 'endpoints' and 'global-service-id'. Use only 'endpoints'.");
        }

        var regions = prodRegions();
        for (var endpoint : endpoints){
            for (var endpointRegion : endpoint.regions()) {
                if (! regions.contains(endpointRegion)) {
                    throw new IllegalArgumentException("Region used in endpoint that is not declared in 'prod': " + endpointRegion);
                }
            }
        }
    }

    /** Validates the given BCP instance (which is owned by this, or if none, a default) against this instance. */
    void validateBcp(Bcp bcp) {
        if (bcp.isEmpty()) return;
        if ( ! prodRegions().equals(bcp.regions()))
            throw new IllegalArgumentException("BCP and deployment mismatch in " + this + ": " +
                                               "A <bcp> element must place all deployed production regions in " +
                                               "at least one group, and declare no extra regions. " +
                                               "Deployed regions: " + prodRegions() +
                                               ". BCP regions: " + bcp.regions());
}
    /** Returns the production regions the steps of this specifies a deployment to. */
    private Set<RegionName> prodRegions() {
        return steps().stream()
                      .flatMap(s -> s.zones().stream())
                      .filter(zone -> zone.environment().isProduction())
                      .flatMap(z -> z.region().stream())
                      .collect(Collectors.toSet());
     }

    private void validateChangeBlockers(List<DeploymentSpec.ChangeBlocker> changeBlockers, Instant now) {
        // Find all possible dates an upgrade block window can start
        Stream<Instant> blockingFrom = changeBlockers.stream()
                                                     .filter(blocker -> blocker.blocksVersions())
                                                     .map(blocker -> blocker.window())
                                                     .map(window -> window.dateRange().start()
                                                                          .map(date -> date.atStartOfDay(window.zone())
                                                                                           .toInstant())
                                                                          .orElse(now))
                                                     .distinct();
        if (!blockingFrom.allMatch(this::canUpgradeWithinDeadline)) {
            throw new IllegalArgumentException("Cannot block Vespa upgrades for longer than " +
                                               maxUpgradeBlockingDays + " consecutive days");
        }
    }

    /** Returns whether this allows upgrade within deadline, relative to given instant */
    private boolean canUpgradeWithinDeadline(Instant instant) {
        instant = instant.truncatedTo(ChronoUnit.HOURS);
        Duration step = Duration.ofHours(1);
        Duration max = Duration.ofDays(maxUpgradeBlockingDays);
        for (Instant current = instant; ! canUpgradeAt(current); current = current.plus(step)) {
            Duration blocked = Duration.between(instant, current);
            if (blocked.compareTo(max) > 0) {
                return false;
            }
        }
        return true;
    }

    /** Returns the upgrade policy of this, which is {@link DeploymentSpec.UpgradePolicy#defaultPolicy} by default */
    public DeploymentSpec.UpgradePolicy upgradePolicy() { return upgradePolicy; }

    /** Returns the revision target choice of this, which is {@link DeploymentSpec.RevisionTarget#latest} by default */
    public DeploymentSpec.RevisionTarget revisionTarget() { return revisionTarget; }

    /** Returns the revision change strategy of this, which is {@link DeploymentSpec.RevisionChange#whenFailing} by default */
    public DeploymentSpec.RevisionChange revisionChange() { return revisionChange; }

    /** Returns the upgrade rollout strategy of this, which is {@link DeploymentSpec.UpgradeRollout#separate} by default */
    public DeploymentSpec.UpgradeRollout upgradeRollout() { return upgradeRollout; }

    /** Minimum cumulative, enqueued risk required for a new revision to roll out to this instance. 0 by default. */
    public int minRisk() { return minRisk; }

    /** Maximum cumulative risk that will automatically roll out to this instance, as long as this is possible. 0 by default. */
    public int maxRisk() { return maxRisk; }

    /* Maximum number of hours to wait for enqueued risk to reach the minimum, before rolling out whatever revisions are enqueued. 8 by default. */
    public int maxIdleHours() { return maxIdleHours; }

    /** Returns time windows where upgrades are disallowed for these instances */
    public List<DeploymentSpec.ChangeBlocker> changeBlocker() { return changeBlockers; }

    /** Returns the ID of the service to expose through global routing, if present */
    public Optional<String> globalServiceId() { return globalServiceId; }

    /** Returns whether the instances in this step can upgrade at the given instant */
    public boolean canUpgradeAt(Instant instant) {
        return changeBlockers.stream().filter(block -> block.blocksVersions())
                                      .noneMatch(block -> block.window().includes(instant));
    }

    /** Returns whether an application revision change for these instances can occur at the given instant */
    public boolean canChangeRevisionAt(Instant instant) {
        return changeBlockers.stream().filter(block -> block.blocksRevisions())
                             .noneMatch(block -> block.window().includes(instant));
    }

    /** Returns the athenz service for environment/region if configured, defaulting to that of the instance */
    public Optional<AthenzService> athenzService(Environment environment, RegionName region) {
        return zones().stream()
                      .filter(zone -> zone.concerns(environment, Optional.of(region)))
                      .findFirst()
                      .flatMap(DeploymentSpec.DeclaredZone::athenzService)
                      .or(() -> this.athenzService);
    }

    /** Returns the cloud account to use for given environment and region, if any */
    public Optional<CloudAccount> cloudAccount(Environment environment, Optional<RegionName> region) {
        return zones().stream()
                      .filter(zone -> zone.concerns(environment, region))
                      .findFirst()
                      .flatMap(DeploymentSpec.DeclaredZone::cloudAccount)
                      .or(() -> cloudAccount);
    }

    /** Returns the notification configuration of these instances */
    public Notifications notifications() { return notifications; }

    /** Returns the rotations configuration of these instances */
    public List<Endpoint> endpoints() { return endpoints; }

    /** Returns the BCP spec of this instance, or BcpSpec.empty() if none. */
    public Bcp bcp() { return bcp; }

    /** Returns whether this instance deploys to the given zone, either implicitly or explicitly */
    public boolean deploysTo(Environment environment, RegionName region) {
        return zones().stream().anyMatch(zone -> zone.concerns(environment, Optional.of(region)));
    }

    /** Returns the zone endpoint specified for the given region, or empty. */
    Optional<ZoneEndpoint> zoneEndpoint(ZoneId zone, ClusterSpec.Id cluster) {
        return Optional.ofNullable(zoneEndpoints.get(cluster))
                       .filter(__ -> deploysTo(zone.environment(), zone.region()))
                       .map(zoneEndpoints -> zoneEndpoints.get(zoneEndpoints.containsKey(zone) ? zone : null));
    }

    /** Returns the zone endpoint data for this instance. */
    Map<ClusterSpec.Id, Map<ZoneId, ZoneEndpoint>> zoneEndpoints() {
        return zoneEndpoints;
    }

    /** The zone endpoints in the given zone, possibly default values. */
    public Map<ClusterSpec.Id, ZoneEndpoint> zoneEndpoints(ZoneId zone) {
        return zoneEndpoints.keySet().stream()
                            .collect(Collectors.toMap(cluster -> cluster,
                                                      cluster -> zoneEndpoint(zone, cluster).orElse(ZoneEndpoint.defaultEndpoint)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeploymentInstanceSpec other = (DeploymentInstanceSpec) o;
        return globalServiceId.equals(other.globalServiceId) &&
               upgradePolicy == other.upgradePolicy &&
               revisionTarget == other.revisionTarget &&
               upgradeRollout == other.upgradeRollout &&
               changeBlockers.equals(other.changeBlockers) &&
               steps().equals(other.steps()) &&
               athenzService.equals(other.athenzService) &&
               notifications.equals(other.notifications) &&
               endpoints.equals(other.endpoints) &&
               zoneEndpoints.equals(other.zoneEndpoints) &&
               bcp.equals(other.bcp) &&
               tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalServiceId, upgradePolicy, revisionTarget, upgradeRollout, changeBlockers, steps(), athenzService, notifications, endpoints, zoneEndpoints, bcp, tags);
    }

    int deployableHashCode() {
        List<DeploymentSpec.DeclaredZone> zones = zones().stream().filter(zone -> zone.concerns(prod)).toList();
        Object[] toHash = new Object[zones.size() + 6];
        int i = 0;
        toHash[i++] = name;
        toHash[i++] = endpoints;
        toHash[i++] = zoneEndpoints;
        toHash[i++] = globalServiceId;
        toHash[i++] = tags;
        toHash[i++] = bcp;
        for (DeploymentSpec.DeclaredZone zone : zones)
            toHash[i++] = Objects.hash(zone, zone.athenzService());

        return Arrays.hashCode(toHash);
    }

    @Override
    public String toString() {
        return "instance '" + name + "'";
    }

}
