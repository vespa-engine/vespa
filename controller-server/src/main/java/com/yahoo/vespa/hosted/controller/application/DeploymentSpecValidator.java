// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentSteps;

import java.util.Objects;

/**
 * This contains validators for a {@link DeploymentSpec} that depend on a {@link Controller} to perform validation.
 *
 * @author mpolden
 */
public class DeploymentSpecValidator {

    private final Controller controller;

    public DeploymentSpecValidator(Controller controller) {
        this.controller = Objects.requireNonNull(controller, "controller must be non-null");
    }

    /**
     * Validate the given deploymentSpec
     *
     * @throws IllegalArgumentException if any validations fail
     */
    public void validate(DeploymentSpec deploymentSpec) {
        validateSteps(deploymentSpec);
    }

    /** Verify that each of the production zones listed in the deployment spec exist in this system */
    private void validateSteps(DeploymentSpec deploymentSpec) {
        new DeploymentSteps(deploymentSpec, controller::system).jobs();
        deploymentSpec.zones().stream()
                      .filter(zone -> zone.environment() == Environment.prod)
                      .forEach(zone -> {
                          if ( ! controller.zoneRegistry().hasZone(ZoneId.from(zone.environment(),
                                                                               zone.region().orElse(null)))) {
                              throw new IllegalArgumentException("Zone " + zone + " in deployment spec was not found in this system!");
                          }
                      });
    }

}
