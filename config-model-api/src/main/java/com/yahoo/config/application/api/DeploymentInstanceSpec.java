// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The deployment spec for an application instance
 *
 * @author bratseth
 */
public class DeploymentInstanceSpec extends DeploymentSpec.Steps {

    /** The name of the instance this step deploys */
    private final InstanceName name;

    private final DeploymentSpec.UpgradePolicy upgradePolicy;
    private final List<DeploymentSpec.ChangeBlocker> changeBlockers;
    private final Optional<String> globalServiceId;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;
    private final Notifications notifications;
    private final List<Endpoint> endpoints;

    public DeploymentInstanceSpec(InstanceName name,
                                  List<DeploymentSpec.Step> steps,
                                  DeploymentSpec.UpgradePolicy upgradePolicy,
                                  List<DeploymentSpec.ChangeBlocker> changeBlockers,
                                  Optional<String> globalServiceId,
                                  Optional<AthenzDomain> athenzDomain,
                                  Optional<AthenzService> athenzService,
                                  Notifications notifications,
                                  List<Endpoint> endpoints) {
        super(steps);
        this.name = name;
        this.upgradePolicy = upgradePolicy;
        this.changeBlockers = changeBlockers;
        this.globalServiceId = globalServiceId;
        this.athenzDomain = athenzDomain;
        this.athenzService = athenzService;
        this.notifications = notifications;
        this.endpoints = List.copyOf(validateEndpoints(endpoints, steps()));
        validateZones(new HashSet<>(), this);
        validateEndpoints(steps(), globalServiceId, this.endpoints);
        validateAthenz();
    }

    public InstanceName name() { return name; }

    /**
     * Throws an IllegalArgumentException if any production deployment or test is declared multiple times,
     * or if any production test is declared not after its corresponding deployment.
     *
     * @param deployments previously seen deployments
     * @param step step whose members to validate
     * @return all contained tests
     */
    private static Set<RegionName> validateZones(Set<RegionName> deployments, DeploymentSpec.Step step) {
        if ( ! step.steps().isEmpty()) {
            Set<RegionName> oldDeployments = Set.copyOf(deployments);
            Set<RegionName> tests = new HashSet<>();
            for (DeploymentSpec.Step nested : step.steps()) {
                for (RegionName test : validateZones(deployments, nested)) {
                    if ( ! (step.isOrdered() ? deployments : oldDeployments).contains(test))
                        throw new IllegalArgumentException("tests for prod." + test + " must be after the corresponding deployment in deployment.xml");

                    if ( ! tests.add(test))
                        throw new IllegalArgumentException("tests for prod." + test + " arelisted twice in deployment.xml");
                }
            }
            return tests;
        }
        if (step.concerns(Environment.prod)) {
            if (step.isTest())
                return Set.of(((DeploymentSpec.DeclaredTest) step).region());

            RegionName region = ((DeploymentSpec.DeclaredZone) step).region().get();
            if ( ! deployments.add(region))
                throw new IllegalArgumentException("prod." + region + " is listed twice in deployment.xml");
        }
        return Set.of();
    }

    /** Validates the endpoints and makes sure default values are respected */
    private List<Endpoint> validateEndpoints(List<Endpoint> endpoints, List<DeploymentSpec.Step> steps) {
        Objects.requireNonNull(endpoints, "Missing endpoints parameter");

        var productionRegions = steps.stream()
                                     .filter(step -> step.concerns(Environment.prod))
                                     .flatMap(step -> step.zones().stream())
                                     .flatMap(zone -> zone.region().stream())
                                     .map(RegionName::value)
                                     .collect(Collectors.toSet());

        var rebuiltEndpointsList = new ArrayList<Endpoint>();

        for (var endpoint : endpoints) {
            if (endpoint.regions().isEmpty()) {
                var rebuiltEndpoint = endpoint.withRegions(productionRegions);
                rebuiltEndpointsList.add(rebuiltEndpoint);
            } else {
                rebuiltEndpointsList.add(endpoint);
            }
        }

        return List.copyOf(rebuiltEndpointsList);
    }

    /** Throw an IllegalArgumentException if an endpoint refers to a region that is not declared in 'prod' */
    private void validateEndpoints(List<DeploymentSpec.Step> steps, Optional<String> globalServiceId, List<Endpoint> endpoints) {
        if (globalServiceId.isPresent() && ! endpoints.isEmpty()) {
            throw new IllegalArgumentException("Providing both 'endpoints' and 'global-service-id'. Use only 'endpoints'.");
        }

        var stepZones = steps.stream()
                             .flatMap(s -> s.zones().stream())
                             .flatMap(z -> z.region().stream())
                             .collect(Collectors.toSet());

        for (var endpoint : endpoints){
            for (var endpointRegion : endpoint.regions()) {
                if (! stepZones.contains(endpointRegion)) {
                    throw new IllegalArgumentException("Region used in endpoint that is not declared in 'prod': " + endpointRegion);
                }
            }
        }
    }

    /**
     * Throw an IllegalArgumentException if Athenz configuration violates:
     * domain not configured -> no zone can configure service
     * domain configured -> all zones must configure service
     */
    private void validateAthenz() {
        // If athenz domain is not set, athenz service cannot be set on any level
        if (athenzDomain.isEmpty()) {
            for (DeploymentSpec.DeclaredZone zone : zones()) {
                if (zone.athenzService().isPresent()) {
                    throw new IllegalArgumentException("Athenz service configured for zone: " + zone + ", but Athenz domain is not configured");
                }
            }
            // if athenz domain is not set, athenz service must be set implicitly or directly on all zones.
        } else if (athenzService.isEmpty()) {
            for (DeploymentSpec.DeclaredZone zone : zones()) {
                if (zone.athenzService().isEmpty()) {
                    throw new IllegalArgumentException("Athenz domain is configured, but Athenz service not configured for zone: " + zone);
                }
            }
        }
    }

    /** Returns the upgrade policy of this, which is defaultPolicy if none is specified */
    public DeploymentSpec.UpgradePolicy upgradePolicy() { return upgradePolicy; }

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

    /** Returns the athenz domain if configured */
    public Optional<AthenzDomain> athenzDomain() { return athenzDomain; }

    /** Returns the athenz service for environment/region if configured */
    public Optional<AthenzService> athenzService(Environment environment, RegionName region) {
        return zones().stream()
                      .filter(zone -> zone.concerns(environment, Optional.of(region)))
                      .findFirst()
                      .flatMap(DeploymentSpec.DeclaredZone::athenzService)
                      .or(() -> this.athenzService);
    }

    /** Returns the notification configuration of these instances */
    public Notifications notifications() { return notifications; }

    /** Returns the rotations configuration of these instances */
    public List<Endpoint> endpoints() { return endpoints; }

    /** Returns whether this instance deploys to the given zone, either implicitly or explicitly */
    public boolean deploysTo(Environment environment, RegionName region) {
        return zones().stream().anyMatch(zone -> zone.concerns(environment, Optional.of(region)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeploymentInstanceSpec other = (DeploymentInstanceSpec) o;
        return globalServiceId.equals(other.globalServiceId) &&
               upgradePolicy == other.upgradePolicy &&
               changeBlockers.equals(other.changeBlockers) &&
               steps().equals(other.steps()) &&
               athenzDomain.equals(other.athenzDomain) &&
               athenzService.equals(other.athenzService) &&
               notifications.equals(other.notifications) &&
               endpoints.equals(other.endpoints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalServiceId, upgradePolicy, changeBlockers, steps(), athenzDomain, athenzService, notifications, endpoints);
    }

    @Override
    public String toString() {
        return "instance '" + name + "'";
    }

}
