// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

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
    private final Flavor nodeFlavor;

    public ProvisionedHost(String id, String hostHostname, Flavor hostFlavor, String nodeHostname, Flavor nodeFlavor) {
        this.id = Objects.requireNonNull(id, "Host id must be set");
        this.hostHostname = Objects.requireNonNull(hostHostname, "Host hostname must be set");
        this.hostFlavor = Objects.requireNonNull(hostFlavor, "Host flavor must be set");
        this.nodeHostname = Objects.requireNonNull(nodeHostname, "Node hostname must be set");
        this.nodeFlavor = Objects.requireNonNull(nodeFlavor, "Node flavor must be set");
    }

    /** Generate {@link Node} instance representing the provisioned physical host */
    Node generateHost() {
        return Node.create(id, Set.of(), Set.of(), hostHostname, Optional.empty(), hostFlavor, NodeType.host);
    }

    /** Generate {@link Node} instance representing the node running on this physical host */
    Node generateNode() {
        return Node.createDockerNode(Set.of(), Set.of(), nodeHostname, hostHostname, nodeFlavor, NodeType.tenant);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProvisionedHost that = (ProvisionedHost) o;
        return id.equals(that.id) &&
                hostHostname.equals(that.hostHostname) &&
                hostFlavor.equals(that.hostFlavor) &&
                nodeHostname.equals(that.nodeHostname) &&
                nodeFlavor.equals(that.nodeFlavor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hostHostname, hostFlavor, nodeHostname, nodeFlavor);
    }

    @Override
    public String toString() {
        return "ProvisionedHost{" +
                "id='" + id + '\'' +
                ", hostHostname='" + hostHostname + '\'' +
                ", hostFlavor=" + hostFlavor +
                ", nodeHostname='" + nodeHostname + '\'' +
                ", nodeFlavor=" + nodeFlavor +
                '}';
    }
}
