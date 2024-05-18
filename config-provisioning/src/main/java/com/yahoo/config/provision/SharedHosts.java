package com.yahoo.config.provision;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author hakonhall
 */
public interface SharedHosts {
    static SharedHosts empty() {
        return new SharedHosts() {
            @Override public boolean supportsClusterType(ClusterSpec.Type clusterType) { return false; }
            @Override public boolean hasClusterType(ClusterSpec.Type clusterType) { return false; }
        };
    }

    static SharedHosts ofConstant(boolean supportsClusterType, boolean hasClusterType) {
        return new SharedHosts() {
            @Override public boolean supportsClusterType(ClusterSpec.Type clusterType) { return supportsClusterType; }
            @Override public boolean hasClusterType(ClusterSpec.Type clusterType) { return hasClusterType; }
        };
    }

    /** Whether there are any shared hosts specifically for the given cluster type, or without a cluster type restriction. */
    @JsonIgnore
    boolean supportsClusterType(ClusterSpec.Type clusterType);

    /** Whether there are any shared hosts specifically for the given cluster type. */
    @JsonIgnore
    boolean hasClusterType(ClusterSpec.Type clusterType);
}
