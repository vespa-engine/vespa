// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

/**
 * A node's membership in a cluster. This is a value object.
 * The format is "clusterType/clusterId/groupId/index[/exclusive][/retired]
 *
 * @author bratseth
 */
public class ClusterMembership {

    private ClusterSpec cluster; // final
    private int index; // final
    private boolean retired; // final
    private String stringValue; // final

    protected ClusterMembership() {}

    private ClusterMembership(String stringValue, Version vespaVersion) {
        String[] components = stringValue.split("/");
        if (components.length < 4 || components.length > 6)
            throw new RuntimeException("Could not parse '" + stringValue + "' to a cluster membership. " +
                                       "Expected 'clusterType/clusterId/groupId/index[/retired][/exclusive]'");

        boolean exclusive = false;
        if (components.length > 4) {
            exclusive = components[4].equals("exclusive");
            retired = components[components.length-1].equals("retired");
        }

        this.cluster = ClusterSpec.from(ClusterSpec.Type.valueOf(components[0]), ClusterSpec.Id.from(components[1]),
                                        ClusterSpec.Group.from(Integer.valueOf(components[2])), vespaVersion, exclusive);
        this.index = Integer.parseInt(components[3]);
        this.stringValue = toStringValue();
    }

    private ClusterMembership(ClusterSpec cluster, int index, boolean retired) {
        this.cluster = cluster;
        this.index = index;
        this.retired = retired;
        this.stringValue = toStringValue();
    }

    protected String toStringValue() {
        return cluster.type().name() +
               "/" + cluster.id().value() +
               (cluster.group().isPresent() ? "/" + cluster.group().get().index() : "") +
               "/" + index +
               ( cluster.isExclusive() ? "/exclusive" : "") +
               ( retired ? "/retired" : "");

    }

    /** Returns the cluster this node is a member of */
    public ClusterSpec cluster() { return cluster; }

    /** Returns the index of this node within the cluster */
    public int index() { return index; }

    /** Returns whether the cluster should prepare for this node to be removed */
    public boolean retired() { return retired; }

    /** Returns a copy of this which is retired */
    public ClusterMembership retire() {
        return new ClusterMembership(cluster, index, true);
    }

    /** Returns a copy of this node which is not retired */
    public ClusterMembership unretire() {
        return new ClusterMembership(cluster, index, false);
    }

    // TODO: Remove after April 2018
    public ClusterMembership changeCluster(ClusterSpec newCluster) {
        return with(newCluster);
    }

    public ClusterMembership with(ClusterSpec newCluster) {
        return new ClusterMembership(newCluster, index, retired);
    }

    /**
     * Returns all the information in this as a string which can be used to construct the same ClusterMembership
     * instance using {@link #from}. This string is currently stored in ZooKeeper on running instances.
     */
    public String stringValue() { return stringValue; }

    @Override
    public int hashCode() { return stringValue().hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof ClusterMembership)) return false;
        return ((ClusterMembership)other).stringValue().equals(stringValue());
    }

    @Override
    public String toString() { return stringValue(); }

    public static ClusterMembership from(String stringValue, Version vespaVersion) {
        return new ClusterMembership(stringValue, vespaVersion);
    }

    public static ClusterMembership from(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, false);
    }

    public static ClusterMembership retiredFrom(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, true);
    }

}
