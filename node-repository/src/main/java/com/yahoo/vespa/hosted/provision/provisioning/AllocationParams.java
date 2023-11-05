// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import static java.util.Objects.requireNonNull;

/**
 * Miscellaneous parameters for the preparation of an allocation of a cluster.
 *
 * <p>Ideal for feature flags that guards new code paths in various parts of the allocation code.</p>
 *
 * @param exclusiveClusterType whether nodes must be allocated to hosts that are exclusive to the cluster type
 * @param exclusiveAllocation  whether nodes are allocated exclusively in this instance given this cluster spec.
 *                             Exclusive allocation requires that the wanted node resources matches the advertised
 *                             resources of the node perfectly
 * @param exclusiveProvisioning Whether the nodes of this cluster must be running on hosts that are specifically provisioned for the application
 * @param sharedHost            snapshot of shared-host flag
 * @param makeExclusive         snapshot of make-exclusive flag
 * @author hakonhall
 */
public record AllocationParams(NodeRepository nodeRepository,
                               ApplicationId application,
                               ClusterSpec cluster,
                               boolean exclusiveClusterType,
                               boolean exclusiveAllocation,
                               boolean exclusiveProvisioning,
                               SharedHost sharedHost,
                               boolean makeExclusive) {

    public AllocationParams {
        requireNonNull(nodeRepository, "nodeRepository cannot be null");
        requireNonNull(application, "application cannot be null");
        requireNonNull(cluster, "cluster cannot be null");
        requireNonNull(sharedHost, "sharedHost cannot be null");
    }

    /** The canonical way of constructing an instance: ensures consistencies between the various parameters. */
    public static AllocationParams from(NodeRepository nodeRepository, ApplicationId application, ClusterSpec cluster, Version version) {
        return from(nodeRepository,
                    application,
                    cluster,
                    PermanentFlags.SHARED_HOST.bindTo(nodeRepository.flagSource()).value(),
                    Flags.MAKE_EXCLUSIVE.bindTo(nodeRepository.flagSource())
                                        .with(FetchVector.Dimension.TENANT_ID, application.tenant().value())
                                        .with(FetchVector.Dimension.INSTANCE_ID, application.serializedForm())
                                        .with(FetchVector.Dimension.VESPA_VERSION, version.toFullString())
                                        .value());
    }

    /**
     * Returns the same allocation parameters, but as-if it was built with the given cluster.  Flags are NOT re-evaluated,
     * but exclusivity may change.
     */
    public AllocationParams with(ClusterSpec cluster) { return from(nodeRepository, application, cluster, sharedHost, makeExclusive); }

    private static AllocationParams from(NodeRepository nodeRepository, ApplicationId application, ClusterSpec cluster, SharedHost sharedHost, boolean makeExclusive) {
        return new AllocationParams(nodeRepository,
                                    application,
                                    cluster,
                                    exclusiveClusterType(cluster, sharedHost),
                                    exclusiveAllocation(nodeRepository.zone(), cluster, sharedHost),
                                    exclusiveProvisioning(nodeRepository.zone(), cluster),
                                    sharedHost,
                                    makeExclusive);
    }

    private static boolean exclusiveClusterType(ClusterSpec cluster, SharedHost sharedHost) {
        return sharedHost.hasClusterType(cluster.type().name());
    }

    private static boolean exclusiveAllocation(Zone zone, ClusterSpec cluster, SharedHost sharedHost) {
        return cluster.isExclusive() ||
               ( cluster.type().isContainer() && zone.system().isPublic() && !zone.environment().isTest() ) ||
               ( !zone.cloud().allowHostSharing() && !sharedHost.supportsClusterType(cluster.type().name()));
    }

    private static boolean exclusiveProvisioning(Zone zone, ClusterSpec clusterSpec) {
        return !zone.cloud().allowHostSharing() && clusterSpec.isExclusive();
    }
}
