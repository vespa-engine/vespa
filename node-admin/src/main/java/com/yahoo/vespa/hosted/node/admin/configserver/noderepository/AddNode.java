// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.host.FlavorOverrides;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
public class AddNode {

    public final String hostname;
    public final Optional<String> parentHostname;
    public final Optional<String> nodeFlavor;
    public final Optional<FlavorOverrides> flavorOverrides;
    public final Optional<NodeResources> nodeResources;
    public final Optional<TenantName> reservedTo;
    public final NodeType nodeType;
    public final Set<String> ipAddresses;
    public final Set<String> additionalIpAddresses;

    public static AddNode forHost(String hostname, String nodeFlavor, Optional<FlavorOverrides> flavorOverrides, Optional<TenantName> reservedTo, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        return new AddNode(hostname, Optional.empty(), Optional.of(nodeFlavor), flavorOverrides, Optional.empty(), reservedTo, nodeType, ipAddresses, additionalIpAddresses);
    }

    public static AddNode forNode(String hostname, String parentHostname, NodeResources nodeResources, NodeType nodeType, Set<String> ipAddresses) {
        return new AddNode(hostname, Optional.of(parentHostname), Optional.empty(), Optional.empty(), Optional.of(nodeResources), Optional.empty(), nodeType, ipAddresses, Set.of());
    }

    private AddNode(String hostname, Optional<String> parentHostname,
                    Optional<String> nodeFlavor, Optional<FlavorOverrides> flavorOverrides,
                    Optional<NodeResources> nodeResources,
                    Optional<TenantName> reservedTo,
                    NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.nodeFlavor = nodeFlavor;
        this.flavorOverrides = flavorOverrides;
        this.nodeResources = nodeResources;
        this.reservedTo = reservedTo;
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
                Objects.equals(parentHostname, addNode.parentHostname) &&
                Objects.equals(nodeFlavor, addNode.nodeFlavor) &&
                Objects.equals(reservedTo, addNode.reservedTo) &&
                nodeType == addNode.nodeType &&
                Objects.equals(ipAddresses, addNode.ipAddresses) &&
                Objects.equals(additionalIpAddresses, addNode.additionalIpAddresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, parentHostname, nodeFlavor, reservedTo, nodeType, ipAddresses, additionalIpAddresses);
    }

    @Override
    public String toString() {
        return "AddNode{" +
                "hostname='" + hostname + '\'' +
                ", parentHostname=" + parentHostname +
                ", nodeFlavor='" + nodeFlavor + '\'' +
               (reservedTo.isPresent() ? ", reservedTo='" + reservedTo.get().value() + "'" : "") +
                ", nodeType=" + nodeType +
                ", ipAddresses=" + ipAddresses +
                ", additionalIpAddresses=" + additionalIpAddresses +
                '}';
    }

}
