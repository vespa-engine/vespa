// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.logging.Level;

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
public class TrafficShareUpdater extends ControllerMaintainer {

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;

    public TrafficShareUpdater(Controller controller, Duration duration) {
        super(controller, duration);
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected double maintain() {
        Exception lastException = null;
        int attempts = 0;
        int failures = 0;
        for (var application : applications.asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instance.deployments().values()) {
                    if ( ! deployment.zone().environment().isProduction()) continue;
                    if (shuttingDown()) return 1.0;
                    try {
                        attempts++;
                        updateTrafficFraction(instance, deployment);
                    }
                    catch (Exception e) {
                        // Some failures due to locked applications are expected and benign
                        failures++;
                        lastException = e;
                    }
                }
            }
        }
        double successFactor = asSuccessFactor(attempts, failures);
        if ( successFactor == 0 )
            log.log(Level.WARNING, "Could not update traffic share on any applications", lastException);
        return successFactor;
    }

    private void updateTrafficFraction(Instance instance, Deployment deployment) {
        double qpsInZone = deployment.metrics().queriesPerSecond();
        double totalQps = instance.deployments().values().stream()
                                                         .filter(i -> i.zone().environment().isProduction())
                                                         .mapToDouble(i -> i.metrics().queriesPerSecond()).sum();
        long prodRegions = instance.deployments().values().stream()
                                                          .filter(i -> i.zone().environment().isProduction())
                                                          .count();
        double currentReadShare = totalQps == 0 ? 0 : qpsInZone / totalQps;
        double maxReadShare = prodRegions < 2 ? 1.0 : 1.0 / ( prodRegions - 1.0);
        if (currentReadShare > maxReadShare) // This can happen because the assumption of equal traffic
            maxReadShare = currentReadShare; // distribution can be incorrect

        nodeRepository.patchApplication(deployment.zone(), instance.id(), currentReadShare, maxReadShare);
    }

}
