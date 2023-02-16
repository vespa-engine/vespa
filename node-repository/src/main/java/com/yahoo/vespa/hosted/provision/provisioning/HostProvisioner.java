// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A service which supports provisioning container hosts dynamically.
 *
 * @author freva
 */
public interface HostProvisioner {

    enum HostSharing {

        /** The host must be provisioned exclusively for the applicationId */
        exclusive,

        /** The host must be provisioned to be shared with other applications. */
        shared,

        /** The client has no requirements on whether the host must be provisioned exclusively or shared. */
        any

    }

    /**
     * Schedule provisioning of a given number of hosts.
     *
     * @param provisionIndices list of unique provision indices which will be used to generate the node hostnames
     *                         on the form of <code>[prefix][index].[domain]</code>
     * @param hostType the host type to provision
     * @param resources the resources needed per node - the provisioned host may be significantly larger
     * @param applicationId id of the application that will own the provisioned host
     * @param osVersion the OS version to use. If this version does not exist, implementations may choose a suitable
     *                  fallback version.
     * @param sharing puts requirements on sharing or exclusivity of the host to be provisioned.
     * @param clusterType the cluster we are provisioning for, or empty if we are provisioning hosts
     *                    to be shared by multiple cluster nodes
     * @param clusterId the id of the cluster we are provisioning for, or empty if we are provisioning hosts
     *                    to be shared by multiple cluster nodes
     * @param cloudAccount the cloud account to use
     * @param provisionedHostConsumer consumer of {@link ProvisionedHost}s describing the provisioned nodes,
     *                                the {@link Node} returned from {@link ProvisionedHost#generateHost()} must be
     *                                written to ZK immediately in case the config server goes down while waiting
     *                                for the provisioning to finish.
     * @throws NodeAllocationException if the cloud provider cannot satisfy the request
     */
    void provisionHosts(List<Integer> provisionIndices,
                        NodeType hostType,
                        NodeResources resources,
                        ApplicationId applicationId,
                        Version osVersion,
                        HostSharing sharing,
                        Optional<ClusterSpec.Type> clusterType,
                        Optional<ClusterSpec.Id> clusterId,
                        CloudAccount cloudAccount,
                        Consumer<List<ProvisionedHost>> provisionedHostConsumer) throws NodeAllocationException;

    /**
     * Continue provisioning of given list of Nodes.
     *
     * @param host the host to provision
     * @param children list of all the nodes that run on the given host
     * @return IP config for the provisioned host and its children
     * @throws FatalProvisioningException if the provisioning has irrecoverably failed and the input nodes
     * should be deleted from node-repo.
     */
    HostIpConfig provision(Node host, Set<Node> children) throws FatalProvisioningException;

    /**
     * Deprovisions a given host and resources associated with it and its children (such as DNS entries).
     * This method will only perform the actual deprovisioning of the host and does NOT:
     *  - verify whether it is safe to do
     *  - clean up config server references to this node or any of its children
     * Therefore, this method should probably only be called for hosts that have no children.
     *
     * @param host host to deprovision.
     */
    void deprovision(Node host);

    /** Replace the root (OS) disk of host. Implementations of this are expected to be idempotent.
     *
     * @return the updated node object
     */
    Node replaceRootDisk(Node host);

    /**
     * Returns the maintenance events scheduled for hosts in this zone, in given cloud accounts. Host events in the
     * zone's default cloud account are always included.
     */
    List<HostEvent> hostEventsIn(List<CloudAccount> cloudAccounts);

}
