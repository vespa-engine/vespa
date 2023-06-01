// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A host provisioning request. This contains the details required to provision a host.
 *
 * @param indices                 List of unique provision indices which will be used to generate the node hostnames
 *                                on the form of <code>[prefix][index].[domain]</code>.
 * @param type                    The host type to provision.
 * @param resources               The resources needed per node - the provisioned host may be significantly larger.
 * @param owner                   ID of the application that will own the provisioned host.
 * @param osVersion               The OS version to use. If this version does not exist, implementations may choose a suitable
 *                                fallback version.
 * @param sharing                 Puts requirements on sharing or exclusivity of the host to be provisioned.
 * @param clusterType             The cluster we are provisioning for, or empty if we are provisioning hosts
 *                                to be shared by multiple cluster nodes.
 * @param clusterId               The ID of the cluster we are provisioning for, or empty if we are provisioning hosts
 *                                to be shared by multiple cluster nodes.
 * @param cloudAccount            The cloud account to use.
 * @param requireLatestGeneration Whether to require the latest generation when choosing a flavor. Latest generation will
 *                                always be preferred, but setting this to true disallows falling back to an older
 *                                generation.
 * @author mpolden
 */
public record HostProvisionRequest(List<Integer> indices,
                                   NodeType type,
                                   NodeResources resources,
                                   ApplicationId owner,
                                   Version osVersion,
                                   HostProvisioner.HostSharing sharing,
                                   Optional<ClusterSpec.Type> clusterType,
                                   Optional<ClusterSpec.Id> clusterId,
                                   CloudAccount cloudAccount,
                                   boolean requireLatestGeneration) {

    public HostProvisionRequest(List<Integer> indices, NodeType type, NodeResources resources,
                                ApplicationId owner, Version osVersion, HostProvisioner.HostSharing sharing,
                                Optional<ClusterSpec.Type> clusterType, Optional<ClusterSpec.Id> clusterId,
                                CloudAccount cloudAccount, boolean requireLatestGeneration) {
        this.indices = List.copyOf(Objects.requireNonNull(indices));
        this.type = Objects.requireNonNull(type);
        this.resources = Objects.requireNonNull(resources);
        this.owner = Objects.requireNonNull(owner);
        this.osVersion = Objects.requireNonNull(osVersion);
        this.sharing = Objects.requireNonNull(sharing);
        this.clusterType = Objects.requireNonNull(clusterType);
        this.clusterId = Objects.requireNonNull(clusterId);
        this.cloudAccount = Objects.requireNonNull(cloudAccount);
        this.requireLatestGeneration = requireLatestGeneration;
    }

}
