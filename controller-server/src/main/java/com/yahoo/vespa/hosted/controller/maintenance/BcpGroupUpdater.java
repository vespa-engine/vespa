// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.Bcp;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.ApplicationPatch;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This computes, for every application deployment
 * - the current fraction of the application's global traffic it receives.
 * - the max fraction it can possibly receive, given its BCP group membership.
 * - for each cluster in the deployment, average statistics from the other members in the group.
 *
 * These values are sent to a config server of each region where it is consumed by autoscaling.
 *
 * It depends on the traffic metrics collected by DeploymentMetricsMaintainer.
 *
 * @author bratseth
 */
public class BcpGroupUpdater extends ControllerMaintainer {

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;
    private final Double successFactorBaseline;

    public BcpGroupUpdater(Controller controller, Duration duration, Double successFactorBaseline) {
        super(controller, duration, successFactorBaseline);
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.successFactorBaseline = successFactorBaseline;
    }

    public BcpGroupUpdater(Controller controller, Duration duration) {
        this(controller, duration, 1.0);
    }

    @Override
    protected double maintain() {
        Exception lastException = null;
        int attempts = 0;
        int failures = 0;
        var metrics = collectClusterMetrics();
        for (var application : applications.asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instance.productionDeployments().values()) {
                    if (shuttingDown()) return 0.0;
                    try {
                        attempts++;
                        var bcpGroups = BcpGroup.groupsFrom(instance, application.deploymentSpec());
                        var patch = new ApplicationPatch();
                        addTrafficShare(deployment, bcpGroups, patch);
                        addBcpGroupInfo(deployment.zone().region(), metrics.get(instance.id()), bcpGroups, patch);
                        nodeRepository.patchApplication(deployment.zone(), instance.id(), patch);
                    }
                    catch (Exception e) {
                        // Some failures due to locked applications are expected and benign
                        failures++;
                        lastException = e;
                    }
                }
            }
        }
        double successFactorDeviation = asSuccessFactorDeviation(attempts, failures);
        if ( successFactorDeviation == -successFactorBaseline )
            log.log(Level.WARNING, "Could not update traffic share on any applications", lastException);
        else if ( successFactorDeviation < -0.1 )
            log.log(Level.FINE, "Could not update traffic share on all applications", lastException);
        return successFactorDeviation;
    }

    /** Adds deployment traffic share to the given patch. */
    private void addTrafficShare(Deployment deployment, List<BcpGroup> bcpGroups, ApplicationPatch patch) {
        // maxReadShare / currentReadShare = how much additional traffic must the zone be able to handle
        double currentReadShare = 0; // How much of the total traffic of the group(s) this is a member of does this deployment receive
        double maxReadShare = 0; // How much of the total traffic of the group(s) this is a member of might this deployment receive if a member of the group fails
        for (BcpGroup group : bcpGroups) {
            if ( ! group.contains(deployment.zone().region())) continue;

            double deploymentQps = deployment.metrics().queriesPerSecond();
            double groupQps = group.totalQps();
            double fraction = group.fraction(deployment.zone().region());
            currentReadShare += groupQps == 0 ? 0 : fraction * deploymentQps / groupQps;
            maxReadShare += group.size() == 1
                           ? currentReadShare
                           : fraction * ( deploymentQps + group.maxQpsExcluding(deployment.zone().region()) / (group.size() - 1) ) / groupQps;
        }
        patch.currentReadShare = currentReadShare;
        patch.maxReadShare = maxReadShare;
    }

    private Map<ApplicationId, Map<ClusterSpec.Id, ClusterDeploymentMetrics>> collectClusterMetrics() {
        Map<ApplicationId, Map<ClusterSpec.Id, ClusterDeploymentMetrics>> metrics = new HashMap<>();
        for (var deploymentEntry : new HashMap<>(controller().applications().deploymentInfo()).entrySet()) {
            if ( ! deploymentEntry.getKey().zoneId().environment().isProduction()) continue;
            var appEntry = metrics.computeIfAbsent(deploymentEntry.getKey().applicationId(), __ -> new HashMap<>());
            for (var clusterEntry : deploymentEntry.getValue().clusters().entrySet()) {
                var clusterMetrics = appEntry.computeIfAbsent(clusterEntry.getKey(), __ -> new ClusterDeploymentMetrics());
                clusterMetrics.put(deploymentEntry.getKey().zoneId().region(),
                                   new DeploymentMetrics(clusterEntry.getValue().target().metrics().queryRate(),
                                                         clusterEntry.getValue().target().metrics().growthRateHeadroom(),
                                                         clusterEntry.getValue().target().metrics().cpuCostPerQuery()));
            }
        }
        return metrics;
    }

    /** Adds bcp group info to the given patch, for any clusters where we have information. */
    private void addBcpGroupInfo(RegionName regionToUpdate, Map<ClusterSpec.Id, ClusterDeploymentMetrics> metrics,
                                 List<BcpGroup> bcpGroups, ApplicationPatch patch) {
        if (metrics == null) return;
        for (var clusterEntry : metrics.entrySet()) {
            addClusterBcpGroupInfo(clusterEntry.getKey(), clusterEntry.getValue(), regionToUpdate, bcpGroups, patch);
        }
    }

    private void addClusterBcpGroupInfo(ClusterSpec.Id id, ClusterDeploymentMetrics metrics,
                                        RegionName regionToUpdate, List<BcpGroup> bcpGroups, ApplicationPatch patch) {
        var weightedSumOfMaxMetrics = DeploymentMetrics.empty();
        double sumOfCompleteMemberships = 0;
        for (BcpGroup bcpGroup : bcpGroups) {
            if ( ! bcpGroup.contains(regionToUpdate)) continue;
            var groupMetrics = metrics.subsetOf(bcpGroup);
            if ( ! groupMetrics.isCompleteExcluding(regionToUpdate, bcpGroup)) continue;
            var max = groupMetrics.maxQueryRateExcluding(regionToUpdate, bcpGroup);
            if (max.isEmpty()) continue;

            weightedSumOfMaxMetrics = weightedSumOfMaxMetrics.add(max.get().multipliedBy(bcpGroup.fraction(regionToUpdate)));
            sumOfCompleteMemberships += bcpGroup.fraction(regionToUpdate);
        }
        if (sumOfCompleteMemberships > 0)
            patch.clusters.put(id.value(), weightedSumOfMaxMetrics.dividedBy(sumOfCompleteMemberships).asClusterPatch());
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

        Set<RegionName> regions() { return regions.keySet(); }

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

    record ApplicationClusterKey(ApplicationId application, ClusterSpec.Id cluster) { }

    static class ClusterDeploymentMetrics {

        private final Map<RegionName, DeploymentMetrics> deploymentMetrics;

        public ClusterDeploymentMetrics() {
            this.deploymentMetrics = new ConcurrentHashMap<>();
        }

        public ClusterDeploymentMetrics(Map<RegionName, DeploymentMetrics> deploymentMetrics) {
            this.deploymentMetrics = new ConcurrentHashMap<>(deploymentMetrics);
        }

        void put(RegionName region, DeploymentMetrics metrics) {
            deploymentMetrics.put(region, metrics);
        }

        ClusterDeploymentMetrics subsetOf(BcpGroup group) {
            Map<RegionName, DeploymentMetrics> filteredMetrics = new HashMap<>();
            for (var entry : deploymentMetrics.entrySet()) {
                if (group.contains(entry.getKey()))
                    filteredMetrics.put(entry.getKey(), entry.getValue());
            }
            return new ClusterDeploymentMetrics(filteredMetrics);
        }

        /** Returns whether this has deployment metrics for each of the deployments in the given instance. */
        boolean isCompleteExcluding(RegionName regionToExclude, BcpGroup bcpGroup) {
            return regionsExcluding(regionToExclude, bcpGroup).allMatch(region -> deploymentMetrics.containsKey(region));
        }

        /** Returns the metrics with the max query rate among the given instance, if any. */
        Optional<DeploymentMetrics> maxQueryRateExcluding(RegionName regionToExclude, BcpGroup bcpGroup) {
            return regionsExcluding(regionToExclude, bcpGroup)
                           .map(region -> deploymentMetrics.get(region))
                           .max(Comparator.comparingDouble(m -> m.queryRate));
        }

        private Stream<RegionName> regionsExcluding(RegionName regionToExclude, BcpGroup bcpGroup) {
            return bcpGroup.regions().stream()
                           .filter(region -> ! region.equals(regionToExclude));
        }

    }

    /** Metrics for a given application, cluster and deployment. */
    record DeploymentMetrics(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {

        public ApplicationPatch.ClusterPatch asClusterPatch() {
            return new ApplicationPatch.ClusterPatch(new ApplicationPatch.BcpGroupInfo(queryRate, growthRateHeadroom, cpuCostPerQuery));
        }

        DeploymentMetrics dividedBy(double d) {
            return new DeploymentMetrics(queryRate / d, growthRateHeadroom / d, cpuCostPerQuery / d);
        }

        DeploymentMetrics multipliedBy(double m) {
            return new DeploymentMetrics(queryRate * m, growthRateHeadroom * m, cpuCostPerQuery * m);
        }

        DeploymentMetrics add(DeploymentMetrics other) {
            return new DeploymentMetrics(queryRate + other.queryRate,
                                         growthRateHeadroom + other.growthRateHeadroom,
                                         cpuCostPerQuery + other.cpuCostPerQuery);
        }

        public static DeploymentMetrics empty() { return new DeploymentMetrics(0, 0, 0); }

    }

}
