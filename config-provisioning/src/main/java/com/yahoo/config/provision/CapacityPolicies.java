// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeResources.DiskSpeed;

import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.config.provision.NodeResources.Architecture;
import static java.util.Objects.requireNonNull;

/**
 * Defines the policies for assigning cluster capacity in various environments.
 *
 * @author bratseth
 */
public class CapacityPolicies {

    private final Zone zone;
    private final Exclusivity exclusivity;
    private final ApplicationId applicationId;
    private final Architecture adminClusterArchitecture;

    public CapacityPolicies(Zone zone, Exclusivity exclusivity, ApplicationId applicationId, Architecture adminClusterArchitecture) {
        this.zone = zone;
        this.exclusivity = exclusivity;
        this.applicationId = applicationId;
        this.adminClusterArchitecture = adminClusterArchitecture;
    }

    public Capacity applyOn(Capacity capacity, boolean exclusive) {
        var min = applyOn(capacity.minResources(), capacity, exclusive);
        var max = applyOn(capacity.maxResources(), capacity, exclusive);
        var groupSize = capacity.groupSize().fromAtMost(max.nodes() / min.groups())
                                            .toAtLeast(min.nodes() / max.groups());
        return capacity.withLimits(min, max, groupSize);
    }

    private ClusterResources applyOn(ClusterResources resources, Capacity capacity, boolean exclusive) {
        int nodes = decideCount(resources.nodes(), capacity.isRequired(), applicationId.instance().isTester());
        int groups = decideGroups(resources.nodes(), resources.groups(), nodes);
        var nodeResources = decideNodeResources(resources.nodeResources(), capacity.isRequired(), exclusive);
        return new ClusterResources(nodes, groups, nodeResources);
    }

    private int decideCount(int requested, boolean required, boolean isTester) {
        if (isTester) return 1;

        if (required) return requested;
        return switch (zone.environment()) {
            case dev, test -> 1;
            case perf -> Math.min(requested, 3);
            case staging -> requested <= 1 ? requested : Math.max(2, requested / 10);
            case prod -> requested;
        };
    }

    private int decideGroups(int requestedNodes, int requestedGroups, int decidedNodes) {
        if (requestedNodes == decidedNodes) return requestedGroups;
        int groups = Math.min(requestedGroups, decidedNodes); // cannot have more groups than nodes
        while (groups > 1 && decidedNodes % groups != 0)
            groups--; // Must be divisible by the number of groups
        return groups;
    }

    private NodeResources decideNodeResources(NodeResources target, boolean required, boolean exclusive) {
        if (required || exclusive) return target;  // Cannot downsize if resources are required, or exclusively allocated
        if (target.isUnspecified()) return target; // Cannot be modified

        if (zone.environment() == Environment.dev && zone.cloud().allowHostSharing()) {
            // Dev does not cap the cpu or network of containers since usage is spotty: Allocate just a small amount exclusively
            target = target.withVcpu(0.1).withBandwidthGbps(0.1);

            // Allocate without GPU in dev
            target = target.with(NodeResources.GpuResources.zero());
        }

        // Allow slow storage in zones which are not performance sensitive
        if (zone.system().isCd() || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            target = target.with(NodeResources.DiskSpeed.any).with(NodeResources.StorageType.any).withBandwidthGbps(0.1);

        return target;
    }

    public ClusterResources specifyFully(ClusterResources resources, ClusterSpec clusterSpec) {
        return resources.with(specifyFully(resources.nodeResources(), clusterSpec));
    }

    public NodeResources specifyFully(NodeResources resources, ClusterSpec clusterSpec) {
        return resources.withUnspecifiedFieldsFrom(defaultResources(clusterSpec).with(DiskSpeed.any));
    }

    private NodeResources defaultResources(ClusterSpec clusterSpec) {
        if (clusterSpec.type() == ClusterSpec.Type.admin) {
            if (exclusivity.allocation(clusterSpec)) {
                return smallestExclusiveResources().with(adminClusterArchitecture);
            }

            if (clusterSpec.id().value().equals("cluster-controllers")) {
                return clusterControllerResources(clusterSpec, adminClusterArchitecture).with(adminClusterArchitecture);
            }

            if (clusterSpec.id().value().equals("logserver")) {
                return logserverResources(adminClusterArchitecture).with(adminClusterArchitecture);
            }

            return versioned(clusterSpec, Map.of(new Version(0), smallestSharedResources())).with(adminClusterArchitecture);
        }

        if (clusterSpec.type() == ClusterSpec.Type.content) {
            // When changing defaults here update cloud.vespa.ai/en/reference/services
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2, 16, 300, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
        else {
            // When changing defaults here update cloud.vespa.ai/en/reference/services
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2.0, 8, 50, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
    }

    private NodeResources clusterControllerResources(ClusterSpec clusterSpec, Architecture architecture) {
        // 1.32 fits floor(8/1.32) = 6 cluster controllers on each 8Gb host, and each will have
        // 1.32-(0.7+0.6)*(1.32/8) = 1.1 Gb real memory given current taxes.
        if (architecture == Architecture.x86_64)
            return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(0.25, 1.32, 10, 0.3)));
        else
            // arm64 nodes need more memory
            return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(0.25, 1.50, 10, 0.3)));
    }

    private NodeResources logserverResources(Architecture architecture) {
        if (zone.cloud().name() == CloudName.AZURE)
            return new NodeResources(2, 4, 50, 0.3);

        if (zone.cloud().name() == CloudName.GCP)
            return new NodeResources(1, 4, 50, 0.3);

        return architecture == Architecture.arm64
                ? new NodeResources(0.5, 2.5, 50, 0.3)
                : new NodeResources(0.5, 2, 50, 0.3);
    }

    // The lowest amount of resources that can be exclusive allocated (i.e. a matching host flavor for this exists)
    private NodeResources smallestExclusiveResources() {
        return zone.cloud().name() == CloudName.AZURE || zone.cloud().name() == CloudName.GCP
                ? new NodeResources(2, 8, 50, 0.3)
                : new NodeResources(0.5, 8, 50, 0.3);
    }

    // The lowest amount of resources that can be shared (i.e. a matching host flavor for this exists)
    private NodeResources smallestSharedResources() {
        return zone.cloud().name() == CloudName.GCP
                ? new NodeResources(1, 4, 50, 0.3)
                : new NodeResources(0.5, 2, 50, 0.3);
    }

    /** Returns whether the nodes requested can share physical host with other applications */
    public ClusterSpec decideExclusivity(Capacity capacity, ClusterSpec requestedCluster) {
        if (capacity.cloudAccount().isPresent()) return requestedCluster.withExclusivity(true); // Implicit exclusive
        boolean exclusive = requestedCluster.isExclusive() && (capacity.isRequired() || zone.environment() == Environment.prod);
        return requestedCluster.withExclusivity(exclusive);
    }

    /**
     * Returns the resources for the newest version not newer than that requested in the cluster spec.
     */
    private static NodeResources versioned(ClusterSpec spec, Map<Version, NodeResources> resources) {
        return requireNonNull(new TreeMap<>(resources).floorEntry(spec.vespaVersion()),
                              "no default resources applicable for " + spec + " among: " + resources)
                       .getValue();
    }

}
