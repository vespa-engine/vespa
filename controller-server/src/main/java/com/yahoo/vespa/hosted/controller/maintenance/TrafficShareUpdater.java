// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.Bcp;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
                for (var deployment : instance.productionDeployments().values()) {
                    if (shuttingDown()) return 1.0;
                    try {
                        attempts++;
                        updateTrafficFraction(instance, deployment, application.deploymentSpec());
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

    private void updateTrafficFraction(Instance instance, Deployment deployment, DeploymentSpec deploymentSpec) {
        // maxReadShare / currentReadShare = how much additional traffic must the zone be able to handle
        double currentReadShare = 0; // How much of the total traffic of the group(s) this is a member of does this deployment receive
        double maxReadShare = 0; // How much of the total traffic of the group(s) this is a member of might this deployment receive if a member of the group fails
        for (BcpGroup group : BcpGroup.groupsFrom(instance, deploymentSpec)) {
            if ( ! group.contains(deployment.zone().region())) continue;

            double deploymentQps = deployment.metrics().queriesPerSecond();
            double groupQps = group.totalQps();
            double fraction = group.fraction(deployment.zone().region());
            currentReadShare += groupQps == 0 ? 0 : fraction * deploymentQps / groupQps;
            maxReadShare += group.size() == 1
                           ? currentReadShare
                           : fraction * ( deploymentQps + group.maxQpsExcluding(deployment.zone().region()) / (group.size() - 1) ) / groupQps;
        }
        nodeRepository.patchApplication(deployment.zone(), instance.id(), currentReadShare, maxReadShare);
    }

    /**
     * A set of regions which will take over traffic from each other if one of them fails.
     * Each region will take an equal share (modulated by fraction) of the failing region's traffic.
     *
     * A regions membership in a group may be partial, represented by a fraction [0, 1],
     * in which case the other regions will collectively only take that fraction of the failing regions traffic,
     * and symmetrically, the region will only take its fraction of its share of traffic of any other failing region.
     */
    private static class BcpGroup {

        /** The instance which has this group. */
        private final Instance instance;

        /** Regions in this group, with their fractions. */
        private final Map<RegionName, Double> regions;

        /** Creates a group of a subset of the deployments in this instance. */
        private BcpGroup(Instance instance, Map<RegionName, Double> regions) {
            this.instance = instance;
            this.regions = regions;
        }

        /** Returns the sum of the fractional memberships of this. */
        double size() {
            return regions.values().stream().mapToDouble(f -> f).sum();
        }

        double fraction(RegionName region) {
            return regions.getOrDefault(region, 0.0);
        }

        boolean contains(RegionName region) {
            return regions.containsKey(region);
        }

        double totalQps() {
            return instance.productionDeployments().values().stream()
                           .mapToDouble(i -> i.metrics().queriesPerSecond()).sum();
        }

        double maxQpsExcluding(RegionName region) {
            return instance.productionDeployments().values().stream()
                           .filter(d -> ! d.zone().region().equals(region))
                           .mapToDouble(d -> d.metrics().queriesPerSecond() * fraction(d.zone().region()))
                           .max()
                           .orElse(0);
        }
        private static Bcp bcpOf(InstanceName instanceName, DeploymentSpec deploymentSpec) {
            var instanceSpec = deploymentSpec.instance(instanceName);
            if (instanceSpec.isEmpty()) return deploymentSpec.bcp();
            return instanceSpec.get().bcp().orElse(deploymentSpec.bcp());
        }

        private static Map<RegionName, Double> regionsFrom(Instance instance) {
            return instance.productionDeployments().values().stream()
                           .collect(Collectors.toMap(deployment -> deployment.zone().region(), __ -> 1.0));
        }

        private static Map<RegionName, Double> regionsFrom(Bcp.Group groupSpec) {
            return groupSpec.members().stream()
                            .collect(Collectors.toMap(member -> member.region(), member -> member.fraction()));
        }

        static List<BcpGroup> groupsFrom(Instance instance, DeploymentSpec deploymentSpec) {
            Bcp bcp = bcpOf(instance.name(), deploymentSpec);
            if (bcp.isEmpty())
                return List.of(new BcpGroup(instance, regionsFrom(instance)));
            return bcp.groups().stream().map(groupSpec -> new BcpGroup(instance, regionsFrom(groupSpec))).toList();
        }

    }

}
