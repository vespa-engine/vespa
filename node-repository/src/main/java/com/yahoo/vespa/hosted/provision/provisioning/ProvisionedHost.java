// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Describes a single newly provisioned host by {@link HostProvisioner}.
 *
 * @author freva
 */
public class ProvisionedHost {

    private final String id;
    private final String hostHostname;
    private final Flavor hostFlavor;
    private final String nodeHostname;
    private final NodeResources nodeResources;

    public ProvisionedHost(String id, String hostHostname, Flavor hostFlavor, String nodeHostname, NodeResources nodeResources) {
        this.id = Objects.requireNonNull(id, "Host id must be set");
        this.hostHostname = Objects.requireNonNull(hostHostname, "Host hostname must be set");
        this.hostFlavor = Objects.requireNonNull(hostFlavor, "Host flavor must be set");
        this.nodeHostname = Objects.requireNonNull(nodeHostname, "Node hostname must be set");
        this.nodeResources = Objects.requireNonNull(nodeResources, "Node resources must be set");
    }

    /** Generate {@link Node} instance representing the provisioned physical host */
    public Node generateHost() {
        return Node.create(id, IP.Config.EMPTY, hostHostname, Optional.empty(), Optional.empty(), hostFlavor, Optional.empty(), NodeType.host);
    }

    /** Generate {@link Node} instance representing the node running on this physical host */
    public Node generateNode() {
        return Node.createDockerNode(Set.of(), nodeHostname, hostHostname, nodeResources, NodeType.tenant);
    }

    public String getId() {
        return id;
    }

    public String hostHostname() {
        return hostHostname;
    }

    public Flavor hostFlavor() {
        return hostFlavor;
    }

    public String nodeHostname() {
        return nodeHostname;
    }

    public NodeResources nodeResources() { return nodeResources; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProvisionedHost that = (ProvisionedHost) o;
        return id.equals(that.id) &&
                hostHostname.equals(that.hostHostname) &&
                hostFlavor.equals(that.hostFlavor) &&
                nodeHostname.equals(that.nodeHostname) &&
                nodeResources.equals(that.nodeResources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hostHostname, hostFlavor, nodeHostname, nodeResources);
    }

    @Override
    public String toString() {
        return "ProvisionedHost{" +
                "id='" + id + '\'' +
                ", hostHostname='" + hostHostname + '\'' +
                ", hostFlavor=" + hostFlavor +
                ", nodeHostname='" + nodeHostname + '\'' +
                ", nodeResources=" + nodeResources +
                '}';
    }

}
