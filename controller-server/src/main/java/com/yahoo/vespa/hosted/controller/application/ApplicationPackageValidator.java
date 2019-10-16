// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;

import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This contains validators for a {@link ApplicationPackage} that depend on a {@link Controller} to perform validation.
 *
 * @author mpolden
 */
public class ApplicationPackageValidator {

    private final Controller controller;

    public ApplicationPackageValidator(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
    }

    /**
     * Validate the given application package
     *
     * @throws IllegalArgumentException if any validations fail
     */
    public void validate(ApplicationPackage applicationPackage) {
        validateSteps(applicationPackage.deploymentSpec());
        validateEndpoints(applicationPackage.deploymentSpec());
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system */
    private void validateSteps(DeploymentSpec deploymentSpec) {
        new DeploymentSteps(deploymentSpec, controller::system).jobs();
        deploymentSpec.instances().stream().flatMap(instance -> instance.zones().stream())
                      .filter(zone -> zone.environment() == Environment.prod)
                      .forEach(zone -> {
                          if ( ! controller.zoneRegistry().hasZone(ZoneId.from(zone.environment(),
                                                                               zone.region().orElse(null)))) {
                              throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                          }
                      });
    }

    /** Verify that no single endpoint contains regions in different clouds */
    private void validateEndpoints(DeploymentSpec deploymentSpec) {
        for (var instance : deploymentSpec.instances()) {
            for (var endpoint : instance.endpoints()) {
                var clouds = new HashSet<CloudName>();
                for (var region : endpoint.regions()) {
                    for (ZoneApi zone : controller.zoneRegistry().zones().all().in(region).zones()) {
                        clouds.add(zone.getCloudName());
                    }
                }
                if (clouds.size() != 1) {
                    throw new IllegalArgumentException("Endpoint '" + endpoint.endpointId() + "' in " + instance +
                                                       " cannot contain regions in different clouds: " +
                                                       endpoint.regions().stream().sorted().collect(Collectors.toList()));
                }
            }
        }
    }

}
