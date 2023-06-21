// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.config.provision.NodeResources.Architecture;
import static com.yahoo.vespa.flags.FetchVector.Dimension.APPLICATION_ID;
import static java.util.Objects.requireNonNull;

/**
 * Defines the policies for assigning cluster capacity in various environments
 *
 * @author bratseth
 * @see NodeResourceLimits
 */
public class CapacityPolicies {

    private final NodeRepository nodeRepository;
    private final Zone zone;
    private final StringFlag adminClusterNodeArchitecture;

    public CapacityPolicies(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
        this.zone = nodeRepository.zone();
        this.adminClusterNodeArchitecture = PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE.bindTo(nodeRepository.flagSource());
    }

    public Capacity applyOn(Capacity capacity, ApplicationId application, boolean exclusive) {
        var min = applyOn(capacity.minResources(), capacity, application, exclusive);
        var max = applyOn(capacity.maxResources(), capacity, application, exclusive);
        var groupSize = capacity.groupSize().fromAtMost(max.nodes() / min.groups())
                                            .toAtLeast(min.nodes() / max.groups());
        return capacity.withLimits(min, max, groupSize);
    }

    private ClusterResources applyOn(ClusterResources resources, Capacity capacity, ApplicationId application, boolean exclusive) {
        int nodes = decideSize(resources.nodes(), capacity.isRequired(), application.instance().isTester());
        int groups = Math.min(resources.groups(), nodes); // cannot have more groups than nodes
        var nodeResources = decideNodeResources(resources.nodeResources(), capacity.isRequired(), exclusive);
        return new ClusterResources(nodes, groups, nodeResources);
    }

    private int decideSize(int requested, boolean required, boolean isTester) {
        if (isTester) return 1;

        if (required) return requested;
        return switch (zone.environment()) {
            case dev, test -> 1;
            case perf -> Math.min(requested, 3);
            case staging -> requested <= 1 ? requested : Math.max(2, requested / 10);
            case prod -> requested;
        };
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

    public ClusterResources specifyFully(ClusterResources resources, ClusterSpec clusterSpec, ApplicationId applicationId) {
        return resources.with(specifyFully(resources.nodeResources(), clusterSpec, applicationId));
    }

    public NodeResources specifyFully(NodeResources resources, ClusterSpec clusterSpec, ApplicationId applicationId) {
        return resources.withUnspecifiedNumbersFrom(defaultResources(clusterSpec, applicationId));
    }

    private NodeResources defaultResources(ClusterSpec clusterSpec, ApplicationId applicationId) {
        if (clusterSpec.type() == ClusterSpec.Type.admin) {
            Architecture architecture = adminClusterArchitecture(applicationId);

            if (nodeRepository.exclusiveAllocation(clusterSpec)) {
                var resources = smallestExclusiveResources();
                return versioned(clusterSpec, Map.of(new Version(0), resources,
                                                     new Version(8, 182, 12), resources.with(architecture)));
            }

            if (clusterSpec.id().value().equals("cluster-controllers")) {
                return clusterControllerResources(clusterSpec, architecture).with(architecture);
            }

            if (clusterSpec.id().value().equals("logserver")) {
                return logserverResources(architecture).with(architecture);
            }

            return versioned(clusterSpec, Map.of(new Version(0), smallestSharedResources())).with(architecture);
        }

        if (zone.environment() == Environment.dev && zone.system() == SystemName.cd) {
            return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 4, 50, 0.3)));
        }

        if (clusterSpec.type() == ClusterSpec.Type.content) {
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2, 16, 300, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
        else {
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2.0, 8, 50, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
    }

    private NodeResources clusterControllerResources(ClusterSpec clusterSpec, Architecture architecture) {
        // 1.32 fits floor(8/1.32) = 6 cluster controllers on each 8Gb host, and each will have
        // 1.32-(0.7+0.6)*(1.32/8) = 1.1 Gb real memory given current taxes.
        if (architecture == Architecture.x86_64)
            return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(0.25, 1.14, 10, 0.3),
                                                 new Version(8, 129, 4), new NodeResources(0.25, 1.32, 10, 0.3)));
        else
            // arm64 nodes need more memory
            return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(0.25, 1.50, 10, 0.3)));
    }

    private NodeResources logserverResources(Architecture architecture) {
        if (zone.cloud().name().equals(CloudName.GCP))
            return new NodeResources(1, 4, 50, 0.3);

        return architecture == Architecture.arm64
                ? new NodeResources(0.5, 2.5, 50, 0.3)
                : new NodeResources(0.5, 2, 50, 0.3);
    }

    private Architecture adminClusterArchitecture(ApplicationId instance) {
        return Architecture.valueOf(adminClusterNodeArchitecture.with(APPLICATION_ID, instance.serializedForm()).value());
    }

    /** Returns the resources for the newest version not newer than that requested in the cluster spec. */
    static NodeResources versioned(ClusterSpec spec, Map<Version, NodeResources> resources) {
        return requireNonNull(new TreeMap<>(resources).floorEntry(spec.vespaVersion()),
                              "no default resources applicable for " + spec + " among: " + resources)
                .getValue();
    }

    // The lowest amount of resources that can be exclusive allocated (i.e. a matching host flavor for this exists)
    private NodeResources smallestExclusiveResources() {
        return (zone.cloud().name().equals(CloudName.GCP))
                ? new NodeResources(1, 4, 50, 0.3)
                : new NodeResources(0.5, 4, 50, 0.3);
    }

    // The lowest amount of resources that can be shared (i.e. a matching host flavor for this exists)
    private NodeResources smallestSharedResources() {
        return (zone.cloud().name().equals(CloudName.GCP))
                ? new NodeResources(1, 4, 50, 0.3)
                : new NodeResources(0.5, 2, 50, 0.3);
    }

    /** Returns whether the nodes requested can share physical host with other applications */
    public ClusterSpec decideExclusivity(Capacity capacity, ClusterSpec requestedCluster) {
        if (capacity.cloudAccount().isPresent()) return requestedCluster.withExclusivity(true); // Implicit exclusive
        boolean exclusive = requestedCluster.isExclusive() && (capacity.isRequired() || zone.environment() == Environment.prod);
        return requestedCluster.withExclusivity(exclusive);
    }

}
