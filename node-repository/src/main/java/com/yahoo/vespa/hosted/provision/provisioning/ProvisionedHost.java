// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.OsVersion;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.util.List;
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
    private final NodeType hostType;
    private final Optional<ApplicationId> exclusiveToApplicationId;
    private final Optional<ClusterSpec.Type> exclusiveToClusterType;
    private final List<HostName> nodeHostnames;
    private final NodeResources nodeResources;
    private final Version osVersion;
    private final CloudAccount cloudAccount;

    public ProvisionedHost(String id, String hostHostname, Flavor hostFlavor, NodeType hostType,
                           Optional<ApplicationId> exclusiveToApplicationId, Optional<ClusterSpec.Type> exclusiveToClusterType,
                           List<HostName> nodeHostnames, NodeResources nodeResources, Version osVersion, CloudAccount cloudAccount) {
        this.id = Objects.requireNonNull(id, "Host id must be set");
        this.hostHostname = Objects.requireNonNull(hostHostname, "Host hostname must be set");
        this.hostFlavor = Objects.requireNonNull(hostFlavor, "Host flavor must be set");
        this.hostType = Objects.requireNonNull(hostType, "Host type must be set");
        this.exclusiveToApplicationId = Objects.requireNonNull(exclusiveToApplicationId, "exclusiveToApplicationId must be set");
        this.exclusiveToClusterType = Objects.requireNonNull(exclusiveToClusterType, "exclusiveToClusterType must be set");
        this.nodeHostnames = validateNodeAddresses(nodeHostnames);
        this.nodeResources = Objects.requireNonNull(nodeResources, "Node resources must be set");
        this.osVersion = Objects.requireNonNull(osVersion, "OS version must be set");
        this.cloudAccount = Objects.requireNonNull(cloudAccount, "Cloud account must be set");
        if (!hostType.isHost()) throw new IllegalArgumentException(hostType + " is not a host");
    }

    private static List<HostName> validateNodeAddresses(List<HostName> nodeHostnames) {
        Objects.requireNonNull(nodeHostnames, "Node hostnames must be set");
        if (nodeHostnames.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one node hostname");
        }
        return nodeHostnames;
    }

    /** Generate {@link Node} instance representing the provisioned physical host */
    public Node generateHost() {
        Node.Builder builder = Node.create(id, IP.Config.of(Set.of(), Set.of(), nodeHostnames), hostHostname, hostFlavor,
                                           hostType)
                                   .status(Status.initial().withOsVersion(OsVersion.EMPTY.withCurrent(Optional.of(osVersion))))
                                   .cloudAccount(cloudAccount);
        exclusiveToApplicationId.ifPresent(builder::exclusiveToApplicationId);
        exclusiveToClusterType.ifPresent(builder::exclusiveToClusterType);
        return builder.build();
    }

    /** Generate {@link Node} instance representing the node running on this physical host */
    public Node generateNode() {
        return Node.reserve(Set.of(), nodeHostname(), hostHostname, nodeResources, hostType.childNodeType())
                .cloudAccount(cloudAccount)
                .build();
    }

    public String getId() { return id; }
    public String hostHostname() { return hostHostname; }
    public Flavor hostFlavor() { return hostFlavor; }
    public NodeType hostType() { return hostType; }
    public Optional<ApplicationId> exclusiveToApplicationId() { return exclusiveToApplicationId; }
    public Optional<ClusterSpec.Type> exclusiveToClusterType() { return exclusiveToClusterType; }
    public List<HostName> nodeHostnames() { return nodeHostnames; }
    public NodeResources nodeResources() { return nodeResources; }
    public Version osVersion() { return osVersion; }
    public CloudAccount cloudAccount() { return cloudAccount; }

    public String nodeHostname() { return nodeHostnames.get(0).value(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProvisionedHost that = (ProvisionedHost) o;
        return id.equals(that.id) &&
               hostHostname.equals(that.hostHostname) &&
               hostFlavor.equals(that.hostFlavor) &&
               hostType == that.hostType &&
               exclusiveToApplicationId.equals(that.exclusiveToApplicationId) &&
               exclusiveToClusterType.equals(that.exclusiveToClusterType) &&
               nodeHostnames.equals(that.nodeHostnames) &&
               nodeResources.equals(that.nodeResources) &&
               osVersion.equals(that.osVersion) &&
               cloudAccount.equals(that.cloudAccount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hostHostname, hostFlavor, hostType, exclusiveToApplicationId, exclusiveToClusterType, nodeHostnames, nodeResources, osVersion, cloudAccount);
    }

    @Override
    public String toString() {
        return "ProvisionedHost{" +
               "id='" + id + '\'' +
               ", hostHostname='" + hostHostname + '\'' +
               ", hostFlavor=" + hostFlavor +
               ", hostType=" + hostType +
               ", exclusiveToApplicationId=" + exclusiveToApplicationId +
               ", exclusiveToClusterType=" + exclusiveToClusterType +
               ", nodeAddresses=" + nodeHostnames +
               ", nodeResources=" + nodeResources +
               ", osVersion=" + osVersion +
               ", cloudAccount=" + cloudAccount +
               '}';
    }

}
