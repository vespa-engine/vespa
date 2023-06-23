// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
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
     * @param request         details of the host provision request.
     * @param whenProvisioned consumer of {@link ProvisionedHost}s describing the provisioned nodes,
     *                        the {@link Node} returned from {@link ProvisionedHost#generateHost} must be
     *                        written to ZK immediately in case the config server goes down while waiting
     *                        for the provisioning to finish.
     * @throws NodeAllocationException if the cloud provider cannot satisfy the request
     */
    void provisionHosts(HostProvisionRequest request, Consumer<List<ProvisionedHost>> whenProvisioned) throws NodeAllocationException;

    /**
     * Continue provisioning of given list of Nodes.
     *
     * @param host the host to provision
     * @return IP config for the provisioned host and its children
     * @throws FatalProvisioningException if the provisioning has irrecoverably failed and the input nodes
     * should be deleted from node-repo.
     */
    HostIpConfig provision(Node host) throws FatalProvisioningException;

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

    /** Returns whether flavor for given host can be upgraded to a newer generation */
    boolean canUpgradeFlavor(Node host, Node child);

}
