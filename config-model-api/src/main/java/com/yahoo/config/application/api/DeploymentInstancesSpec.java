// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The deployment spec for some specified application instances
 *
 * @author bratseth
 */
public class DeploymentInstancesSpec {

    private final Optional<String> globalServiceId;
    private final DeploymentSpec.UpgradePolicy upgradePolicy;
    private final Optional<Integer> majorVersion;
    private final List<DeploymentSpec.ChangeBlocker> changeBlockers;
    private final List<DeploymentSpec.Step> steps;
    private final Optional<AthenzDomain> athenzDomain;
    private final Optional<AthenzService> athenzService;
    private final List<Endpoint> endpoints;

    public DeploymentInstancesSpec(Optional<String> globalServiceId,
                                   DeploymentSpec.UpgradePolicy upgradePolicy,
                                   Optional<Integer> majorVersion,
                                   List<DeploymentSpec.ChangeBlocker> changeBlockers,
                                   List<DeploymentSpec.Step> steps,
                                   Optional<AthenzDomain> athenzDomain,
                                   Optional<AthenzService> athenzService,
                                   List<Endpoint> endpoints) {
        this.globalServiceId = globalServiceId;
        this.upgradePolicy = upgradePolicy;
        this.majorVersion = majorVersion;
        this.changeBlockers = changeBlockers;
        this.steps = List.copyOf(completeSteps(new ArrayList<>(steps)));
        this.athenzDomain = athenzDomain;
        this.athenzService = athenzService;
        this.endpoints = List.copyOf(validateEndpoints(endpoints, this.steps));
        validateZones(this.steps);
        validateEndpoints(this.steps, globalServiceId, this.endpoints);
        validateAthenz();
    }

    /** Adds missing required steps and reorders steps to a permissible order */
    private static List<DeploymentSpec.Step> completeSteps(List<DeploymentSpec.Step> steps) {
        // Add staging if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.prod)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.staging))) {
            steps.add(new DeploymentSpec.DeclaredZone(Environment.staging));
        }

        // Add test if required and missing
        if (steps.stream().anyMatch(step -> step.deploysTo(Environment.staging)) &&
            steps.stream().noneMatch(step -> step.deploysTo(Environment.test))) {
            steps.add(new DeploymentSpec.DeclaredZone(Environment.test));
        }

        // Enforce order test, staging, prod
        DeploymentSpec.DeclaredZone testStep = remove(Environment.test, steps);
        if (testStep != null)
            steps.add(0, testStep);
        DeploymentSpec.DeclaredZone stagingStep = remove(Environment.staging, steps);
        if (stagingStep != null)
            steps.add(1, stagingStep);

        return steps;
    }

    /**
     * Removes the first occurrence of a deployment step to the given environment and returns it.
     *
     * @return the removed step, or null if it is not present
     */
    private static DeploymentSpec.DeclaredZone remove(Environment environment, List<DeploymentSpec.Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).deploysTo(environment))
                return (DeploymentSpec.DeclaredZone)steps.remove(i);
        }
        return null;
    }

    /** Throw an IllegalArgumentException if any production zone is declared multiple times */
    private void validateZones(List<DeploymentSpec.Step> steps) {
        Set<DeploymentSpec.DeclaredZone> zones = new HashSet<>();

        for (DeploymentSpec.Step step : steps)
            for (DeploymentSpec.DeclaredZone zone : step.zones())
                ensureUnique(zone, zones);
    }

    private void ensureUnique(DeploymentSpec.DeclaredZone zone, Set<DeploymentSpec.DeclaredZone> zones) {
        if ( ! zones.add(zone))
            throw new IllegalArgumentException(zone + " is listed twice in deployment.xml");
    }

    /** Validates the endpoints and makes sure default values are respected */
    private List<Endpoint> validateEndpoints(List<Endpoint> endpoints, List<DeploymentSpec.Step> steps) {
        Objects.requireNonNull(endpoints, "Missing endpoints parameter");

        var productionRegions = steps.stream()
                                     .filter(step -> step.deploysTo(Environment.prod))
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

    /**
     * Throw an IllegalArgumentException if Athenz configuration violates:
     * domain not configured -> no zone can configure service
     * domain configured -> all zones must configure service
     */
    private void validateAthenz() {
        // If athenz domain is not set, athenz service cannot be set on any level
        if (athenzDomain.isEmpty()) {
            for (DeploymentSpec.DeclaredZone zone : zones()) {
                if(zone.athenzService().isPresent()) {
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

}
