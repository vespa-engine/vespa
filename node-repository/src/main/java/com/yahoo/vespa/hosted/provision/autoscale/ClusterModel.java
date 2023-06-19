// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.provisioning.CapacityPolicies;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/**
 * A cluster with its associated metrics which allows prediction about its future behavior.
 * For single-threaded, short-term usage.
 *
 * @author bratseth
 */
public class ClusterModel {

    /** Containers typically use more cpu right after generation change, so discard those metrics */
    public static final Duration warmupDuration = Duration.ofMinutes(7);

    /** If we have less than this query rate, we cannot be fully confident in our load data, which influences some decisions. */
    public static final double queryRateGivingFullConfidence = 100.0;

    static final double idealQueryCpuLoad = 0.8;
    static final double idealWriteCpuLoad = 0.95;

    static final double idealContainerMemoryLoad = 0.8;
    static final double idealContentMemoryLoad = 0.65;

    static final double idealContainerDiskLoad = 0.95;
    static final double idealContentDiskLoad = 0.6;

    // Memory for other processes running on the node (config-proxy, metrics-proxy).
    // Keep in sync with config-model/NodeResourcesTuning.
    static final double nodeMemoryOverheadGb = 0.7;

    // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
    // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
    // TODO: Measure this, and only take it into account with queries
    private static final double fixedCpuCostFraction = 0.1;

    private final NodeRepository nodeRepository;
    private final Application application;
    private final ClusterSpec clusterSpec;
    private final Cluster cluster;

    private final CpuModel cpu = new CpuModel();
    private final MemoryModel memory = new MemoryModel();
    private final DiskModel disk = new DiskModel();

    /**
     * The current active nodes of this cluster, including retired,
     * or empty if this models a new cluster not yet deployed.
     */
    private final NodeList nodes;

    private final Clock clock;
    private final Duration scalingDuration;
    private final ClusterTimeseries clusterTimeseries;
    private final ClusterNodesTimeseries nodeTimeseries;
    private final Instant at;

    // Lazily initialized members
    private Double queryFractionOfMax = null;
    private Double maxQueryGrowthRate = null;
    private OptionalDouble averageQueryRate = null;

    public ClusterModel(NodeRepository nodeRepository,
                        Application application,
                        ClusterSpec clusterSpec,
                        Cluster cluster,
                        NodeList clusterNodes,
                        MetricsDb metricsDb,
                        Clock clock) {
        this.nodeRepository = nodeRepository;
        this.application = application;
        this.clusterSpec = clusterSpec;
        this.cluster = cluster;
        this.nodes = clusterNodes;
        this.clock = clock;
        this.scalingDuration = cluster.scalingDuration(clusterSpec);
        this.clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());
        this.nodeTimeseries = new ClusterNodesTimeseries(scalingDuration(), cluster, nodes, metricsDb);
        this.at = clock.instant();
    }

    ClusterModel(NodeRepository nodeRepository,
                 Application application,
                 ClusterSpec clusterSpec,
                 Cluster cluster,
                 Clock clock,
                 Duration scalingDuration,
                 ClusterTimeseries clusterTimeseries,
                 ClusterNodesTimeseries nodeTimeseries) {
        this.nodeRepository = nodeRepository;
        this.application = application;
        this.clusterSpec = clusterSpec;
        this.cluster = cluster;
        this.nodes = NodeList.of();
        this.clock = clock;

        this.scalingDuration = scalingDuration;
        this.clusterTimeseries = clusterTimeseries;
        this.nodeTimeseries = nodeTimeseries;
        this.at = clock.instant();
    }

    public Application application() { return application; }
    public ClusterSpec clusterSpec() { return clusterSpec; }
    private ClusterNodesTimeseries nodeTimeseries() { return nodeTimeseries; }
    private ClusterTimeseries clusterTimeseries() { return clusterTimeseries; }

    /** Returns the instant this model was created. */
    public Instant at() { return at;}

    public boolean isEmpty() {
        return nodeTimeseries().isEmpty();
    }

    /** Returns the predicted duration of a rescaling of this cluster */
    public Duration scalingDuration() { return scalingDuration; }

    /** Returns the average of the peak load measurement in each dimension, from each node. */
    public Load peakLoad() {
        return nodeTimeseries().peakLoad();
    }

    /** Returns the relative load adjustment accounting for redundancy in this. */
    public Load redundancyAdjustment() {
        return loadWith(nodeCount(), groupCount());
    }

    /** Returns the relative load adjustment that should be made to this cluster given available measurements. */
    public Load loadAdjustment() {
        if (nodeTimeseries().measurementsPerNode() < 0.5) return Load.one(); // Don't change based on very little data
        Load adjustment = peakLoad().divide(idealLoad());
        if (! safeToScaleDown())
            adjustment = adjustment.map(v -> v < 1 ? 1 : v);
        return adjustment;
    }

    public boolean isStable(NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (nodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().removable()))
            return false;

        // A deployment is ongoing
        if ( ! nodeRepository.nodes().list(Node.State.reserved, Node.State.provisioned).owner(application.id()).isEmpty())
            return false;

        return true;
    }

    /** Are we in a position to make decisions to scale down at this point? */
    public boolean safeToScaleDown() {
        if (hasScaledIn(scalingDuration().multipliedBy(3))) return false;
        if (nodeTimeseries().nodesMeasured() != nodeCount()) return false;
        return true;
    }

    public static Duration minScalingDuration(ClusterSpec clusterSpec) {
        if (clusterSpec.isStateful()) return Duration.ofHours(6);
        return Duration.ofMinutes(5);
    }

    /**
     * Returns the relative load adjustment accounting for redundancy given these nodes+groups
     * relative to node nodes+groups in this.
     */
    public Load loadWith(int trueNodes, int trueGroups) {
        int nodes = nodesAdjustedForRedundancy(trueNodes, trueGroups);
        int groups = groupsAdjustedForRedundancy(trueNodes, trueGroups);
        if (clusterSpec().type() == ClusterSpec.Type.content) { // load scales with node share of content
            int groupSize = nodes / groups;

            // Cpu: Query cpu scales with cluster size, write cpu scales with group size
            // Memory and disk: Scales with group size

            // The fixed cost portion of cpu does not scale with changes to the node count
            double queryCpuPerGroup = fixedCpuCostFraction + (1 - fixedCpuCostFraction) * groupSize() / groupSize;

            double queryCpu = queryCpuPerGroup * groupCount() / groups;
            double writeCpu = (double)groupSize() / groupSize;
            return new Load(cpu.queryFraction() * queryCpu + (1 - cpu.queryFraction()) * writeCpu,
                            (1 - memory.fixedFraction()) * (double)groupSize() / groupSize + memory.fixedFraction() * 1,
                            (double)groupSize() / groupSize);
        }
        else {
            return new Load((double)nodeCount() / nodes, 1, 1);
        }
    }

    /**
     * Returns the ideal load across the nodes of this such that each node will be at ideal load
     * if one of the nodes go down.
     */
    public Load idealLoad() {
        var ideal = new Load(cpu.idealLoad(), memory.idealLoad(), disk.idealLoad()).divide(redundancyAdjustment());
        if ( !cluster.bcpGroupInfo().isEmpty() && cluster.bcpGroupInfo().queryRate() > 0) {
            // Since we have little local information, use information about query cost in other groups

            Load bcpGroupIdeal = adjustQueryDependentIdealLoadByBcpGroupInfo(ideal);

            // Do a weighted sum of the ideal "vote" based on local and bcp group info.
            // This avoids any discontinuities with a near-zero local query rate.
            double localInformationWeight = Math.min(1, averageQueryRate().orElse(0) /
                                                        Math.min(queryRateGivingFullConfidence, cluster.bcpGroupInfo().queryRate()));
            ideal = ideal.multiply(localInformationWeight).add(bcpGroupIdeal.multiply(1 - localInformationWeight));
        }
        return ideal;
    }

    private boolean canRescaleWithinBcpDeadline() {
        return scalingDuration().minus(cluster.clusterInfo().bcpDeadline()).isNegative();
    }

    public Autoscaling.Metrics metrics() {
        return new Autoscaling.Metrics(averageQueryRate().orElse(0),
                                       growthRateHeadroom(),
                                       cpu.costPerQuery().orElse(0));
    }

    private Load adjustQueryDependentIdealLoadByBcpGroupInfo(Load ideal) {
        double currentClusterTotalVcpuPerGroup = nodes.not().retired().first().get().resources().vcpu() * groupSize();

        double targetQueryRateToHandle = ( canRescaleWithinBcpDeadline() ? averageQueryRate().orElse(0)
                                                                         : cluster.bcpGroupInfo().queryRate() )
                                         * cluster.bcpGroupInfo().growthRateHeadroom() * trafficShiftHeadroom();
        double neededTotalVcpPerGroup = cluster.bcpGroupInfo().cpuCostPerQuery() * targetQueryRateToHandle / groupCount() +
                                        ( 1 - cpu.queryFraction()) * cpu.idealLoad() *
                                        (clusterSpec.type().isContainer() ? 1 : groupSize());

        double cpuAdjustment = neededTotalVcpPerGroup / currentClusterTotalVcpuPerGroup;
        return ideal.withCpu(peakLoad().cpu() / cpuAdjustment);
    }

    private boolean hasScaledIn(Duration period) {
        if (cluster.lastScalingEvent().isEmpty()) return false;
        var lastCompletion = cluster.lastScalingEvent().get().completion();
        if (lastCompletion.isEmpty()) return true; // Ongoing
        return lastCompletion.get().isAfter(clock.instant().minus(period));
    }

    /**
     * Returns the predicted max query growth rate per minute as a fraction of the average traffic
     * in the scaling window.
     */
    private double maxQueryGrowthRate() {
        if (maxQueryGrowthRate != null) return maxQueryGrowthRate;
        return maxQueryGrowthRate = clusterTimeseries().maxQueryGrowthRate(scalingDuration(), clock);
    }

    /** Returns the average query rate in the scaling window as a fraction of the max observed query rate. */
    private double queryFractionOfMax() {
        if (queryFractionOfMax != null) return queryFractionOfMax;
        return queryFractionOfMax = clusterTimeseries().queryFractionOfMax(scalingDuration(), clock);
    }

    /** Returns the average query rate in the scaling window. */
    private OptionalDouble averageQueryRate() {
        if (averageQueryRate != null) return averageQueryRate;
        return averageQueryRate = clusterTimeseries().queryRate(scalingDuration(), clock);
    }

    /** The number of nodes this cluster has, or will have if not deployed yet. */
    // TODO: Make this the deployed, not current count
    private int nodeCount() {
        if ( ! nodes.isEmpty()) return (int)nodes.not().retired().stream().count();
        return cluster.minResources().nodes();
    }

    /** The number of groups this cluster has, or will have if not deployed yet. */
    // TODO: Make this the deployed, not current count
    private int groupCount() {
        if ( ! nodes.isEmpty()) return (int)nodes.not().retired().stream().mapToInt(node -> node.allocation().get().membership().cluster().group().get().index()).distinct().count();
        return cluster.minResources().groups();
    }

    private int groupSize() {
        // ceil: If the division does not produce a whole number we assume some node is missing
        return (int)Math.ceil((double)nodeCount() / groupCount());
    }

    private int nodesAdjustedForRedundancy(int nodes, int groups) {
        int groupSize = (int)Math.ceil((double)nodes / groups);
        return nodes > 1 ? (groups == 1 ? nodes - 1 : nodes - groupSize) : nodes;
    }

    private int groupsAdjustedForRedundancy(int nodes, int groups) {
        return nodes > 1 ? (groups == 1 ? 1 : groups - 1) : groups;
    }

    /** Returns the headroom for growth during organic traffic growth as a multiple of current resources. */
    private double growthRateHeadroom() {
        if ( ! nodeRepository.zone().environment().isProduction()) return 1;
        double growthRateHeadroom = 1 + maxQueryGrowthRate() * scalingDuration().toMinutes();
        // Cap headroom at 10% above the historical observed peak
        if (queryFractionOfMax() != 0)
            growthRateHeadroom = Math.min(growthRateHeadroom, 1 / queryFractionOfMax() + 0.1);

        return adjustByConfidence(growthRateHeadroom);
    }

    /**
     * Returns the headroom is needed to handle sudden arrival of additional traffic due to another zone going down
     * as a multiple of current resources.
     */
    private double trafficShiftHeadroom() {
        if ( ! nodeRepository.zone().environment().isProduction()) return 1;
        if (canRescaleWithinBcpDeadline()) return 1;
        double trafficShiftHeadroom;
        if (application.status().maxReadShare() == 0) // No traffic fraction data
            trafficShiftHeadroom = 2.0; // assume we currently get half of the max possible share of traffic
        else if (application.status().currentReadShare() == 0)
            trafficShiftHeadroom = 1/application.status().maxReadShare();
        else
            trafficShiftHeadroom = application.status().maxReadShare() / application.status().currentReadShare();
        return adjustByConfidence(Math.min(trafficShiftHeadroom, 1/application.status().maxReadShare()));
    }

    /**
     * Headroom values are a multiplier of the current query rate.
     * Adjust this value closer to 1 if the query rate is too low to derive statistical conclusions
     * with high confidence to avoid large adjustments caused by random noise due to low traffic numbers.
     */
    private double adjustByConfidence(double headroom) {
        return ( (headroom -1 ) * Math.min(1, averageQueryRate().orElse(0) / queryRateGivingFullConfidence) ) + 1;
    }

    private class CpuModel {

        /** Ideal cpu load must take the application traffic fraction into account. */
        double idealLoad() {
            double queryCpuFraction = queryFraction();

            // Assumptions: 1) Write load is not organic so we should not grow to handle more.
            //                 (TODO: But allow applications to set their target write rate and size for that)
            //              2) Write load does not change in BCP scenarios.
            return queryCpuFraction * 1/growthRateHeadroom() * 1/trafficShiftHeadroom() * idealQueryCpuLoad +
                   (1 - queryCpuFraction) * idealWriteCpuLoad;
        }

        OptionalDouble costPerQuery() {
            if (averageQueryRate().isEmpty() || averageQueryRate().getAsDouble() == 0.0) return OptionalDouble.empty();
            // TODO: Query rate should generally be sampled at the time where we see the peak resource usage
            int fanOut = clusterSpec.type().isContainer() ? 1 : groupSize();
            return OptionalDouble.of(peakLoad().cpu()  * cpu.queryFraction() * fanOut * nodes.not().retired().first().get().resources().vcpu()
                                     / averageQueryRate().getAsDouble() / groupCount());
        }

        /** The estimated fraction of cpu usage which goes to processing queries vs. writes */
        double queryFraction() {
            OptionalDouble writeRate = clusterTimeseries().writeRate(scalingDuration(), clock);
            if (averageQueryRate().orElse(0) == 0 && writeRate.orElse(0) == 0) return queryFraction(0.5);
            return queryFraction(averageQueryRate().orElse(0) / (averageQueryRate().orElse(0) + writeRate.orElse(0)));
        }

        double queryFraction(double queryRateFraction) {
            double relativeQueryCost = 9; // How much more expensive are queries than writes? TODO: Measure
            double writeFraction = 1 - queryRateFraction;
            return queryRateFraction * relativeQueryCost / (queryRateFraction * relativeQueryCost + writeFraction);
        }

    }

    private class MemoryModel {

        double idealLoad() {
            if (clusterSpec.type().isContainer()) return idealContainerMemoryLoad;
            if (clusterSpec.type() == ClusterSpec.Type.admin) return idealContainerMemoryLoad; // Not autoscaled, but ideal shown in console
            return idealContentMemoryLoad;
        }

        /**
         * Returns the fraction of memory of the current allocation which is currently consumed by
         * fixed data structures which take the same amount of space regardless of document volume.
         */
        double fixedFraction() {
            if (clusterSpec().type().isContainer()) return 1.0;
            double fixedMemory = nodeMemoryOverheadGb +
                                 (averageReal() - nodeMemoryOverheadGb) * 0.05; // TODO: Measure actual content node usage
            return fixedMemory / averageReal();
        }

        double averageReal() {
            if (nodes.isEmpty()) { // we're estimating
                var initialResources = new CapacityPolicies(nodeRepository).specifyFully(cluster.minResources().nodeResources(),
                                                                                         clusterSpec,
                                                                                         application.id());
                return nodeRepository.resourcesCalculator().requestToReal(initialResources,
                                                                          nodeRepository.exclusiveAllocation(clusterSpec),
                                                                          false).memoryGb();
            }
            else {
                return nodes.stream()
                            .mapToDouble(node -> nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository).memoryGb())
                            .average()
                            .getAsDouble();
            }
        }

    }

    private class DiskModel {

        double idealLoad() {
            // Stateless clusters are not expected to consume more disk over time -
            // if they do it is due to logs which will be rotated away right before the disk is full
            return clusterSpec.isStateful() ? idealContentDiskLoad : idealContainerDiskLoad;
        }

    }

}
