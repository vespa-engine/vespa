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
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.JacksonFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.flags.custom.SharedHost;
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

    private final Zone zone;
    private final JacksonFlag<SharedHost> sharedHosts;
    private final StringFlag adminClusterNodeArchitecture;

    public CapacityPolicies(NodeRepository nodeRepository) {
        this.zone = nodeRepository.zone();
        this.sharedHosts = PermanentFlags.SHARED_HOST.bindTo(nodeRepository.flagSource());
        this.adminClusterNodeArchitecture = PermanentFlags.ADMIN_CLUSTER_NODE_ARCHITECTURE.bindTo(nodeRepository.flagSource());
    }

    public Capacity applyOn(Capacity capacity, ApplicationId application, boolean exclusive) {
        return capacity.withLimits(applyOn(capacity.minResources(), capacity, application, exclusive),
                                   applyOn(capacity.maxResources(), capacity, application, exclusive));
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

        // Dev does not cap the cpu or network of containers since usage is spotty: Allocate just a small amount exclusively
        if (zone.environment() == Environment.dev && zone.cloud().allowHostSharing())
            target = target.withVcpu(0.1).withBandwidthGbps(0.1);

        // Allow slow storage in zones which are not performance sensitive
        if (zone.system().isCd() || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            target = target.with(NodeResources.DiskSpeed.any).with(NodeResources.StorageType.any).withBandwidthGbps(0.1);

        return target;
    }

    public NodeResources defaultNodeResources(ClusterSpec clusterSpec, ApplicationId applicationId, boolean exclusive) {
        if (clusterSpec.type() == ClusterSpec.Type.admin) {
            Architecture architecture = adminClusterArchitecture(applicationId);

            if (clusterSpec.id().value().equals("cluster-controllers")) {
                return clusterControllerResources(clusterSpec, exclusive)
                        .with(architecture);
            }

            return (requiresExclusiveHost(clusterSpec.type(), exclusive)
                    ? versioned(clusterSpec, Map.of(new Version(0), smallestExclusiveResources()))
                    : versioned(clusterSpec, Map.of(new Version(0), smallestSharedResources())))
                    .with(architecture);
        }

        if (clusterSpec.type() == ClusterSpec.Type.content) {
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2.0, 8, 50, 0.3),
                                                   new Version(8, 75), new NodeResources(2, 16, 300, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
        else {
            return zone.cloud().dynamicProvisioning()
                   ? versioned(clusterSpec, Map.of(new Version(0), new NodeResources(2.0, 8, 50, 0.3)))
                   : versioned(clusterSpec, Map.of(new Version(0), new NodeResources(1.5, 8, 50, 0.3)));
        }
    }

    private NodeResources clusterControllerResources(ClusterSpec clusterSpec, boolean exclusive) {
        if (requiresExclusiveHost(clusterSpec.type(), exclusive)) {
            return versioned(clusterSpec, Map.of(new Version(0), smallestExclusiveResources()));
        }
        return versioned(clusterSpec, Map.of(new Version(0), new NodeResources(0.25, 1.14, 10, 0.3)));
    }

    private Architecture adminClusterArchitecture(ApplicationId instance) {
        return Architecture.valueOf(adminClusterNodeArchitecture.with(APPLICATION_ID, instance.serializedForm()).value());
    }

    /** Returns whether an exclusive host is required for given cluster type and exclusivity requirement */
    private boolean requiresExclusiveHost(ClusterSpec.Type type, boolean exclusive) {
        return ! zone.cloud().allowHostSharing() && (exclusive || !sharedHosts.value().isEnabled(type.name()));
    }

    /** Returns the resources for the newest version not newer than that requested in the cluster spec. */
    static NodeResources versioned(ClusterSpec spec, Map<Version, NodeResources> resources) {
        return requireNonNull(new TreeMap<>(resources).floorEntry(spec.vespaVersion()),
                              "no default resources applicable for " + spec + " among: " + resources)
                .getValue();
    }

    // The lowest amount resources that can be exclusive allocated (i.e. a matching host flavor for this exists)
    private NodeResources smallestExclusiveResources() {
        return (zone.cloud().name().equals(CloudName.GCP))
                ? new NodeResources(1, 4, 50, 0.3)
                : new NodeResources(0.5, 4, 50, 0.3);
    }

    // The lowest amount resources that can be shared (i.e. a matching host flavor for this exists)
    private NodeResources smallestSharedResources() {
        return (zone.cloud().name().equals(CloudName.GCP))
                ? new NodeResources(1, 4, 50, 0.3)
                : new NodeResources(0.5, 2, 50, 0.3);
    }

    /** Returns whether the nodes requested can share physical host with other applications */
    public boolean decideExclusivity(Capacity capacity, boolean requestedExclusivity) {
        if (capacity.cloudAccount().isPresent()) return true; // Implicit exclusive when using custom cloud account
        return requestedExclusivity && (capacity.isRequired() || zone.environment() == Environment.prod);
    }

}
