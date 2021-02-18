// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;

/**
 * This computes, for every application deployment
 * - the current fraction of the application's global traffic it receives
 * - the max fraction it can possibly receive, assuming traffic is evenly distributed over regions
 *   and max one region is down at any time. (We can let deployment.xml override these assumptions later).
 *
 * These two numbers are sent to a config server of each region where it is ultimately
 * consumed by autoscaling.
 *
 * It depends on the traffic metrics collected by DeploymentMetricsMaintainer.
 *
 * @author bratseth
 */
public class TrafficFractionUpdater extends ControllerMaintainer {

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;

    public TrafficFractionUpdater(Controller controller, Duration duration) {
        super(controller, duration, DeploymentMetricsMaintainer.class.getSimpleName(), SystemName.all());
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected boolean maintain() {
        for (var application : applications.asList()) {
            System.out.println("Application " + application);
            for (var instance : application.instances().values()) {
                System.out.println("  Instance " + instance);
                for (var deployment : instance.deployments().values()) {
                    if ( ! deployment.zone().environment().isProduction()) continue;
                    System.out.println("    Deployment " + deployment);
                    updateTrafficFraction(instance, deployment);
                }
            }
        }
        return true;
    }

    private void updateTrafficFraction(Instance instance, Deployment deployment) {
        double qpsInZone = deployment.metrics().queriesPerSecond();
        double totalQps = instance.deployments().values().stream()
                                                         .filter(i -> i.zone().environment().isProduction())
                                                         .mapToDouble(i -> i.metrics().queriesPerSecond()).sum();
        long prodRegions = instance.deployments().values().stream()
                                                          .filter(i -> i.zone().environment().isProduction())
                                                          .count();
        System.out.println("      qps in zone: " + qpsInZone + ", total qps: " + totalQps + ", prod regions: " +  prodRegions);
        double currentTrafficFraction = totalQps == 0 ? 0 : qpsInZone / totalQps;
        double maxTrafficFraction = prodRegions < 2 ? 1.0 : 1.0 / ( prodRegions - 1.0);
        if (currentTrafficFraction > maxTrafficFraction) // This can happen because the assumption of equal traffic
            maxTrafficFraction = currentTrafficFraction; // distribution can be incorrect

        nodeRepository.setTrafficFraction(deployment.zone(), instance.id(), currentTrafficFraction, maxTrafficFraction);
    }

}
