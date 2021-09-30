// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

import java.util.Objects;
import java.util.Optional;

/**
 * A node's membership in a cluster. This is a value object.
 * The format is "clusterType/clusterId/groupId/index[/exclusive][/retired][/stateful][/combinedId]"
 *
 * @author bratseth
 */
public class ClusterMembership {

    private ClusterSpec cluster; // final
    private int index; // final
    private boolean retired; // final
    private String stringValue; // final

    protected ClusterMembership() {}

    private ClusterMembership(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo) {
        String[] components = stringValue.split("/");
        if (components.length < 4)
            throw new RuntimeException("Could not parse '" + stringValue + "' to a cluster membership. " +
                                       "Expected 'clusterType/clusterId/groupId/index[/retired][/exclusive][/stateful][/combinedId]'");

        boolean exclusive = false;
        boolean stateful = false;
        var combinedId = Optional.<String>empty();
        if (components.length > 4) {
            for (int i = 4; i < components.length; i++) {
                String component = components[i];
                switch (component) {
                    case "exclusive": exclusive = true; break;
                    case "retired": retired = true; break;
                    case "stateful": stateful = true; break;
                    default: combinedId = Optional.of(component); break;
                }
            }
        }

        this.cluster = ClusterSpec.specification(ClusterSpec.Type.valueOf(components[0]),
                                                 ClusterSpec.Id.from(components[1]))
                                  .group(ClusterSpec.Group.from(Integer.parseInt(components[2])))
                                  .vespaVersion(vespaVersion)
                                  .exclusive(exclusive)
                                  .combinedId(combinedId.map(ClusterSpec.Id::from))
                                  .dockerImageRepository(dockerImageRepo)
                                  .stateful(stateful)
                                  .build();
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
               ( retired ? "/retired" : "") +
               ( cluster.isStateful() ? "/stateful" : "") +
               ( cluster.combinedId().isPresent() ? "/" + cluster.combinedId().get().value() : "");

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

    public ClusterMembership with(ClusterSpec newCluster) {
        return new ClusterMembership(newCluster, index, retired);
    }

    /**
     * Returns all the information in this as a string which can be used to construct the same ClusterMembership
     * instance using {@link #from}. This string is currently stored in ZooKeeper on running instances.
     */
    public String stringValue() { return stringValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterMembership that = (ClusterMembership) o;
        return index == that.index &&
               retired == that.retired &&
               cluster.equals(that.cluster) &&
               stringValue.equals(that.stringValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cluster, index, retired, stringValue);
    }

    @Override
    public String toString() { return stringValue(); }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo) {
        return new ClusterMembership(stringValue, vespaVersion, dockerImageRepo);
    }

    public static ClusterMembership from(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, false);
    }

    public static ClusterMembership retiredFrom(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, true);
    }

}
