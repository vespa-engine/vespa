// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.NodeType;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
public class AddNode {

    public final String hostname;
    public final Optional<String> parentHostname;
    public final String nodeFlavor;
    public final NodeType nodeType;
    public final Set<String> ipAddresses;
    public final Set<String> additionalIpAddresses;

    /**
     * Constructor for a host node (has no parent)
     */
    public AddNode(String hostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this(hostname, Optional.empty(), nodeFlavor, nodeType, ipAddresses, additionalIpAddresses);
    }

    /**
     * Constructor for a child node (Must set parentHostname, no additionalIpAddresses)
     */
    public AddNode(String hostname, String parentHostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses) {
        this(hostname, Optional.of(parentHostname), nodeFlavor, nodeType, ipAddresses, Collections.emptySet());
    }

    public AddNode(String hostname, Optional<String> parentHostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.nodeFlavor = nodeFlavor;
        this.nodeType = nodeType;
        this.ipAddresses = ipAddresses;
        this.additionalIpAddresses = additionalIpAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AddNode addNode = (AddNode) o;

        if (!hostname.equals(addNode.hostname)) return false;
        if (!parentHostname.equals(addNode.parentHostname)) return false;
        if (!nodeFlavor.equals(addNode.nodeFlavor)) return false;
        if (nodeType != addNode.nodeType) return false;
        if (!ipAddresses.equals(addNode.ipAddresses)) return false;
        return additionalIpAddresses.equals(addNode.additionalIpAddresses);
    }

    @Override
    public int hashCode() {
        int result = hostname.hashCode();
        result = 31 * result + parentHostname.hashCode();
        result = 31 * result + nodeFlavor.hashCode();
        result = 31 * result + nodeType.hashCode();
        result = 31 * result + ipAddresses.hashCode();
        result = 31 * result + additionalIpAddresses.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AddNode{" +
                "hostname='" + hostname + '\'' +
                ", parentHostname=" + parentHostname +
                ", nodeFlavor='" + nodeFlavor + '\'' +
                ", nodeType=" + nodeType +
                ", ipAddresses=" + ipAddresses +
                ", additionalIpAddresses=" + additionalIpAddresses +
                '}';
    }
}
