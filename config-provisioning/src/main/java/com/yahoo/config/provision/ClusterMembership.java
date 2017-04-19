// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;

import java.util.Optional;

/**
 * A node's membership in a cluster.
 * This is a value object.
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
        String restValue;
        if (stringValue.endsWith("/retired")) {
            retired = true;
            restValue = stringValue.substring(0, stringValue.length() - "/retired".length());
        }
        else {
            retired = false;
            restValue = stringValue;
        }

        String[] components = restValue.split("/");

        if ( components.length == 3) // Aug 2016: This should never happen any more
            initWithoutGroup(components, vespaVersion);
        else if (components.length == 4)
            initWithGroup(components, vespaVersion);
        else
            throw new RuntimeException("Could not parse '" + stringValue + "' to a cluster membership. " +
                                       "Expected 'id/type.index[/group]'");

        this.stringValue = toStringValue();
    }

    private ClusterMembership(ClusterSpec cluster, int index, boolean retired) {
        this.cluster = cluster;
        this.index = index;
        this.retired = retired;
        this.stringValue = toStringValue();
    }

    private void initWithoutGroup(String[] components, Version vespaVersion) {
        this.cluster = ClusterSpec.request(ClusterSpec.Type.valueOf(components[0]),
                                           ClusterSpec.Id.from(components[1]),
                                           vespaVersion);
        this.index = Integer.parseInt(components[2]);
    }

    private void initWithGroup(String[] components, Version vespaVersion) {
        this.cluster = ClusterSpec.from(ClusterSpec.Type.valueOf(components[0]), ClusterSpec.Id.from(components[1]),
                                        ClusterSpec.Group.from(Integer.valueOf(components[2])), vespaVersion);
        this.index = Integer.parseInt(components[3]);
    }

    protected String toStringValue() {
        return cluster.type().name() + "/" + cluster.id().value() +
                ( cluster.group().isPresent() ? "/" + cluster.group().get().index() : "") + "/" + index +
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

    public ClusterMembership changeCluster(ClusterSpec newCluster) {
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

    @Deprecated
    // TODO: April 2017 - Remove this when no version older than 6.92 is in production
    public static ClusterMembership from(String stringValue, Optional<String> dockerImage) {
        return from(stringValue, dockerImage.map(DockerImage::new).map(DockerImage::tagAsVersion).orElse(Vtag.currentVersion));
    }

    @Deprecated
    // TODO: April 2017 - Remove this when no version older than 6.97 is in production
    public static ClusterMembership fromVersion(String stringValue, Optional<Version> vespaVersion) {
        return new ClusterMembership(stringValue, vespaVersion.orElse(Vtag.currentVersion));
    }

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
