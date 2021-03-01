// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;

import java.util.List;
import java.util.Set;

/**
 * Service for provisioning physical docker tenant hosts inside the zone.
 *
 * @author freva
 */
public interface HostProvisioner {

    enum HostSharing {
        /** The host must be provisioned exclusively for the applicationId */
        exclusive,

        /** The host must be provisioned to be shared with other applications. \
         */
        shared,

        /** The client has no requirements on whether the host must be provisio\
         ned exclusively or shared. */
        any
    }

    /**
     * Schedule provisioning of a given number of hosts.
     *
     * @param provisionIndices list of unique provision indices which will be used to generate the node hostnames
     *                         on the form of <code>[prefix][index].[domain]</code>
     * @param hostType The host type to provision
     * @param resources the resources needed per node - the provisioned host may be significantly larger
     * @param applicationId id of the application that will own the provisioned host
     * @param osVersion the OS version to use. If this version does not exist, implementations may choose a suitable
     *                  fallback version.
     * @param sharing puts requirements on sharing or exclusivity of the host to be provisioned.
     * @return list of {@link ProvisionedHost} describing the provisioned nodes
     */
    List<ProvisionedHost> provisionHosts(List<Integer> provisionIndices,
                                         NodeType hostType,
                                         NodeResources resources,
                                         ApplicationId applicationId,
                                         Version osVersion,
                                         HostSharing sharing);

    /**
     * Continue provisioning of given list of Nodes.
     *
     * @param host the host to provision
     * @param children list of all the nodes that run on the given host
     * @return a subset of {@code host} and {@code children} where the values have been modified and should
     * be written back to node-repository.
     * @throws FatalProvisioningException if the provisioning has irrecoverably failed and the input nodes
     * should be deleted from node-repo.
     */
    List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException;

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

}
