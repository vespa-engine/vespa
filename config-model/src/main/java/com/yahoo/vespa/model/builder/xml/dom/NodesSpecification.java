// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;

import java.util.Map;
import java.util.Optional;

/**
 * A common utility class to represent a requirement for some nodes during model building.
 * Such a requirement is commonly specified as a <code>nodes</code> element.
 *
 * @author bratseth
 */
 // TODO: Use this for all nodes tags and unify with NodesUtil
public class NodesSpecification {

    private final boolean dedicated;

    private final int count;

    private final int groups;

    private final Optional<String> flavor;

    private final Optional<String> dockerImage;

    private NodesSpecification(boolean dedicated, int count, int groups, Optional<String> flavor, Optional<String> dockerImage) {
        this.dedicated = dedicated;
        this.count = count;
        this.groups = groups;
        this.flavor = flavor;
        this.dockerImage = dockerImage;
    }

    private NodesSpecification(boolean dedicated, ModelElement nodesElement) {
        this(dedicated,
             nodesElement.requiredIntegerAttribute("count"),
             nodesElement.getIntegerAttribute("groups", 1),
             Optional.ofNullable(nodesElement.getStringAttribute("flavor")),
             Optional.ofNullable(nodesElement.getStringAttribute("docker-image")));
    }

    /**
     * Returns a requirement for dedicated nodes taken from the given <code>nodes</code> element
     */
    public static NodesSpecification from(ModelElement nodesElement) {
        return new NodesSpecification(true, nodesElement);
    }

    /**
     * Returns a requirement for dedicated nodes taken from the <code>nodes</code> element
     * contained in the given parent element, or empty if the parent element is null, or the nodes elements
     * is not present.
     */
    public static Optional<NodesSpecification> fromParent(ModelElement parentElement) {
        if (parentElement == null) return Optional.empty();
        ModelElement nodesElement = parentElement.getChild("nodes");
        if (nodesElement == null) return Optional.empty();
        return Optional.of(from(nodesElement));
    }

    /**
     * Returns a requirement for undedicated or dedicated nodes taken from the <code>nodes</code> element
     * contained in the given parent element, or empty if the parent element is null, or the nodes elements
     * is not present.
     */
    public static Optional<NodesSpecification> optionalDedicatedFromParent(ModelElement parentElement) {
        if (parentElement == null) return Optional.empty();
        ModelElement nodesElement = parentElement.getChild("nodes");
        if (nodesElement == null) return Optional.empty();
        return Optional.of(new NodesSpecification(nodesElement.getBooleanAttribute("dedicated", false), nodesElement));
    }

    /** Returns a requirement from <code>count</code> nondedicated nodes in one group */
    public static NodesSpecification nonDedicated(int count) {
        return new NodesSpecification(false, count, 1, Optional.empty(), Optional.empty());
    }

    /**
     * Returns whether this requires dedicated nodes.
     * Otherwise the model encountering this request should reuse nodes requested for other purposes whenever possible.
     */
    public boolean isDedicated() { return dedicated; }

    /** Returns the number of nodes required */
    public int count() { return count; }

    /** Returns the number of host groups this specifies. Default is 1 */
    public int groups() { return groups; }

    public Map<HostResource, ClusterMembership> provision(HostSystem hostSystem, ClusterSpec.Type clusterType, ClusterSpec.Id clusterId, Optional<ClusterSpec.Group> clusterGroup, DeployLogger logger) {
        if (clusterGroup.isPresent() && groups > 1)
            throw new IllegalArgumentException("Cannot both specify a group and request multiple groups");
        ClusterSpec cluster = ClusterSpec.from(clusterType, clusterId, clusterGroup, dockerImage);
        return hostSystem.allocateHosts(cluster, Capacity.fromNodeCount(count, flavor), groups, logger);
    }


}
