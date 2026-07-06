// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.component.Version;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A node's membership in a cluster. This is a value object.
 * The format is "clusterType/clusterId/groupId/index[/exclusive][/retired][/stateful]"
 *
 * @author bratseth
 */
public class ClusterMembership {

    private final ClusterSpec cluster;

    private final ClusterSpec.Type type;
    private final ClusterSpec.Id id;
    private final int group;
    private final int index;
    private final boolean retired;

    private final String stringValue;

    private ClusterMembership(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo,
                              ZoneEndpoint zoneEndpoint, List<SidecarSpec> sidecars, List<AzName> availabilityZones,
                              Optional<String> profile) {
        String[] components = stringValue.split("/");
        if (components.length < 3)
            throw new RuntimeException("Could not parse '" + stringValue + "' to a cluster membership. " +
                                       "Expected 'clusterType/clusterId/groupId/index[/retired][/exclusive][/stateful]'");

        Integer groupIndex = components[2].isEmpty() ? null : Integer.parseInt(components[2]);
        Integer nodeIndex;
        int missingElements = 0;
        try {
            nodeIndex = Integer.parseInt(components[3]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Legacy form missing the group component
            nodeIndex = groupIndex;
            groupIndex = null;
            missingElements = 1;
        }

        boolean exclusive = false;
        boolean stateful = false;
        boolean retired = false;
        if (components.length > (4 - missingElements)) {
            for (int i = (4 - missingElements); i < components.length; i++) {
                String component = components[i];
                switch (component) {
                    case "exclusive" -> exclusive = true;
                    case "retired" -> retired = true;
                    case "stateful" -> stateful = true;
                }
            }
        }

        this.cluster = ClusterSpec.specification(ClusterSpec.Type.valueOf(components[0]),
                                                 ClusterSpec.Id.from(components[1]))
                                  .group(groupIndex == null ? null : ClusterSpec.Group.from(groupIndex))
                                  .vespaVersion(vespaVersion)
                                  .exclusive(exclusive)
                                  .dockerImageRepository(dockerImageRepo)
                                  .loadBalancerSettings(zoneEndpoint)
                                  .stateful(stateful)
                                  .sidecars(sidecars)
                                  .availabilityZones(availabilityZones)
                                  .profile(profile.orElse(null))
                                  .build();
        this.type = cluster.type();
        this.id = cluster.id();
        this.group = groupIndex == null ? 0 : groupIndex;
        this.index = nodeIndex;
        this.retired = retired;
        this.stringValue = toStringValue();
    }

    private ClusterMembership(ClusterSpec cluster, int index, boolean retired) {
        this.cluster = cluster;
        this.type = cluster.type();
        this.id = cluster.id();
        this.group = cluster.group().map(ClusterSpec.Group::index).orElse(0);
        this.index = index;
        this.retired = retired;
        this.stringValue = toStringValue();
    }

    protected String toStringValue() {
        return cluster.type().name() +
               "/" + cluster.id().value() +
               (cluster.group().isPresent() ? "/" + cluster.group().get().index() : "/") +
               "/" + index +
               ( cluster.isExclusive() ? "/exclusive" : "") +
               ( retired ? "/retired" : "") +
               ( cluster.isStateful() ? "/stateful" : "");
    }

    /** Returns the cluster this node is a member of */
    public ClusterSpec cluster() { return cluster; }

    /** Returns the type of the cluster this belongs to. */
    public ClusterSpec.Type type() { return type; }

    /** Returns the id of the cluster this belongs to. */
    public ClusterSpec.Id id() { return id; }

    /** Returns the index of the group this node belongs to. */
    public int group() { return group; }

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
        return from(stringValue, vespaVersion, dockerImageRepo, ZoneEndpoint.defaultEndpoint);
    }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo,
                                         ZoneEndpoint zoneEndpoint) {
        return from(stringValue, vespaVersion, dockerImageRepo, zoneEndpoint, List.of());
    }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo, List<SidecarSpec> sidecars) {
        return from(stringValue, vespaVersion, dockerImageRepo, ZoneEndpoint.defaultEndpoint, sidecars);
    }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo,
                                         ZoneEndpoint zoneEndpoint, List<SidecarSpec> sidecars) {
        return from(stringValue, vespaVersion, dockerImageRepo, zoneEndpoint, sidecars, List.of());
    }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo,
                                         ZoneEndpoint zoneEndpoint, List<SidecarSpec> sidecars, List<AzName> availabilityZones) {
        return from(stringValue, vespaVersion, dockerImageRepo, zoneEndpoint, sidecars, availabilityZones, Optional.empty());
    }

    public static ClusterMembership from(String stringValue, Version vespaVersion, Optional<DockerImage> dockerImageRepo,
                                         ZoneEndpoint zoneEndpoint, List<SidecarSpec> sidecars, List<AzName> availabilityZones,
                                         Optional<String> profile) {
        return new ClusterMembership(stringValue, vespaVersion, dockerImageRepo, zoneEndpoint, sidecars, availabilityZones, profile);
    }

    public static ClusterMembership from(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, false);
    }

    public static ClusterMembership retiredFrom(ClusterSpec cluster, int index) {
        return new ClusterMembership(cluster, index, true);
    }

}
