// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.host.FlavorOverrides;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
public class AddNode {

    public final String hostname;
    public final Optional<String> id;
    public final Optional<String> parentHostname;
    public final Optional<String> nodeFlavor;
    public final Optional<FlavorOverrides> flavorOverrides;
    public final Optional<NodeResources> nodeResources;
    public final NodeType nodeType;
    public final Set<String> ipAddresses;
    public final Set<String> additionalIpAddresses;

    public static AddNode forHost(String hostname, Optional<String> id, String nodeFlavor, Optional<FlavorOverrides> flavorOverrides, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        return new AddNode(hostname, id, Optional.empty(), Optional.of(nodeFlavor), flavorOverrides, Optional.empty(), nodeType, ipAddresses, additionalIpAddresses);
    }

    public static AddNode forNode(String hostname, String parentHostname, NodeResources nodeResources, NodeType nodeType, Set<String> ipAddresses) {
        return new AddNode(hostname, Optional.empty(), Optional.of(parentHostname), Optional.empty(), Optional.empty(), Optional.of(nodeResources), nodeType, ipAddresses, Set.of());
    }

    private AddNode(String hostname, Optional<String> id, Optional<String> parentHostname,
                    Optional<String> nodeFlavor, Optional<FlavorOverrides> flavorOverrides,
                    Optional<NodeResources> nodeResources,
                    NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this.hostname = hostname;
        this.id = id;
        this.parentHostname = parentHostname;
        this.nodeFlavor = nodeFlavor;
        this.flavorOverrides = flavorOverrides;
        this.nodeResources = nodeResources;
        this.nodeType = nodeType;
        this.ipAddresses = ipAddresses;
        this.additionalIpAddresses = additionalIpAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddNode addNode = (AddNode) o;
        return Objects.equals(hostname, addNode.hostname) &&
                Objects.equals(id, addNode.id) &&
                Objects.equals(parentHostname, addNode.parentHostname) &&
                Objects.equals(nodeFlavor, addNode.nodeFlavor) &&
                Objects.equals(flavorOverrides, addNode.flavorOverrides) &&
                Objects.equals(nodeResources, addNode.nodeResources) &&
                nodeType == addNode.nodeType &&
                Objects.equals(ipAddresses, addNode.ipAddresses) &&
                Objects.equals(additionalIpAddresses, addNode.additionalIpAddresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, id, parentHostname, nodeFlavor, flavorOverrides, nodeResources, nodeType, ipAddresses, additionalIpAddresses);
    }

    @Override
    public String toString() {
        return "AddNode{" +
                "hostname='" + hostname + '\'' +
                ", id=" + id +
                ", parentHostname=" + parentHostname +
                ", nodeFlavor='" + nodeFlavor + '\'' +
                ", flavorOverrides='" + flavorOverrides + '\'' +
                ", nodeResources='" + nodeResources + '\'' +
                ", nodeType=" + nodeType +
                ", ipAddresses=" + ipAddresses +
                ", additionalIpAddresses=" + additionalIpAddresses +
                '}';
    }

}
