// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A service which supports provisioning container hosts dynamically.
 *
 * @author freva
 */
public interface HostProvisioner {

    enum HostSharing {

        /** The host must be provisioned exclusively for the application ID. */
        provision,

        /** The host must be exclusive to a single application ID */
        exclusive,

        /** The host may be provisioned to be shared with other applications, otherwise falls back to exclusive. */
        shared;

        public boolean isExclusiveAllocation() {
            return this == provision || this == exclusive;
        }

    }

    /**
     * Schedule provisioning of a given number of hosts.
     *
     * @param request         details of the host provision request.
     * @param realHostResourcesWithinLimits  predicate that returns true if the given resources are within allowed limits
     * @param whenProvisioned consumer of {@link ProvisionedHost}s describing the provisioned nodes,
     *                        the {@link Node} returned from {@link ProvisionedHost#generateHost} must be
     *                        written to ZK immediately in case the config server goes down while waiting
     *                        for the provisioning to finish.
     * @throws NodeAllocationException if the cloud provider cannot satisfy the request
     * @return a runnable that waits for the provisioning request to finish. It can be run without holding any locks,
     * but may fail with an exception that should be propagated to the user initiating prepare()
     */
    Runnable provisionHosts(HostProvisionRequest request, Predicate<NodeResources> realHostResourcesWithinLimits, Consumer<List<ProvisionedHost>> whenProvisioned) throws NodeAllocationException;

    /**
     * Continue provisioning of the given host.
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
     * @return true if the was successfully deprovisioned, false if the deprovisioning is still in progress. This method
     *         should be called again later until it returns true.
     */
    boolean deprovision(Node host);

    /** Replace the root (OS) disk of hosts. Implementations of this are expected to be idempotent.
     *
     * @return the node objects for which updates were made
     */
    default RebuildResult replaceRootDisk(Collection<Node> hosts) { return new RebuildResult(List.of(), Map.of()); }

    record RebuildResult(List<Node> rebuilt, Map<Node, Exception> failed) { }

    /**
     * Returns the maintenance events scheduled for hosts in this zone, in given cloud accounts. Host events in the
     * zone's default cloud account are always included.
     */
    List<HostEvent> hostEventsIn(List<CloudAccount> cloudAccounts);

    /** Returns whether flavor for given host can be upgraded to a newer generation */
    boolean canUpgradeFlavor(Node host, Node child, Predicate<NodeResources> realHostResourcesWithinLimits);

    /** Returns all OS versions available to host for the given major version */
    Set<Version> osVersions(Node host, int majorVersion);

}
