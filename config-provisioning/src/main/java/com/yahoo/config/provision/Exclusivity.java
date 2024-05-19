package com.yahoo.config.provision;

/**
 * A class which can be asked if allocations should be exclusive.
 *
 * @author bratseth
 */
public class Exclusivity {

    private final Zone zone;
    private final SharedHosts sharedHost;

    public Exclusivity(Zone zone, SharedHosts sharedHost) {
        this.zone = zone;
        this.sharedHost = sharedHost;
    }

    /** Returns whether nodes must be allocated to hosts that are exclusive to the cluster type. */
    public boolean clusterType(ClusterSpec cluster) {
        return sharedHost.hasClusterType(cluster.type());
    }

    /** Returns whether the nodes of this cluster must be running on hosts that are specifically provisioned for the application. */
    public boolean provisioning(ClusterSpec clusterSpec) {
        return !zone.cloud().allowHostSharing() && clusterSpec.isExclusive();
    }

    /**
     * Returns whether nodes are allocated exclusively in this instance given this cluster spec.
     * Exclusive allocation requires that the wanted node resources matches the advertised resources of the node
     * perfectly.
     */
    public boolean allocation(ClusterSpec clusterSpec) {
        return clusterSpec.isExclusive() ||
               ( clusterSpec.type().isContainer() && zone.system().isPublic() && !zone.environment().isTest() ) ||
               ( !zone.cloud().allowHostSharing() && !sharedHost.supportsClusterType(clusterSpec.type()));
    }

}
