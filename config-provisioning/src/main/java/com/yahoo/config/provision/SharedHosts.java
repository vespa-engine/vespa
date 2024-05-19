package com.yahoo.config.provision;

/**
 * @author hakonhall
 */
public interface SharedHosts {

    /** Whether there are any shared hosts specifically for the given cluster type, or without a cluster type restriction. */
    boolean supportsClusterType(ClusterSpec.Type clusterType);

    /** Whether there are any shared hosts specifically for the given cluster type. */
    boolean hasClusterType(ClusterSpec.Type clusterType);

    static SharedHosts empty() { return ofConstant(false, false); }

    static SharedHosts ofConstant(boolean supportsClusterType, boolean hasClusterType) {
        return new SharedHosts() {
            @Override public boolean supportsClusterType(ClusterSpec.Type clusterType) { return supportsClusterType; }
            @Override public boolean hasClusterType(ClusterSpec.Type clusterType) { return hasClusterType; }
        };
    }

}
