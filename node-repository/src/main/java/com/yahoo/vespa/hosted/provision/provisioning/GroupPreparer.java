// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Performs preparation of node activation changes for a single host group in an application.
 *
 * @author bratseth
 */
public class GroupPreparer {

    private static final Mutex PROBE_LOCK = () -> {};

    private final NodeRepository nodeRepository;
    private final Optional<HostProvisioner> hostProvisioner;
    private final StringFlag allocateOsRequirementFlag;
    private final BooleanFlag provisionConfigServerDynamically;

    public GroupPreparer(NodeRepository nodeRepository,
                         Optional<HostProvisioner> hostProvisioner,
                         FlagSource flagSource) {
        this.nodeRepository = nodeRepository;
        this.hostProvisioner = hostProvisioner;
        this.allocateOsRequirementFlag = Flags.ALLOCATE_OS_REQUIREMENT.bindTo(flagSource);
        this.provisionConfigServerDynamically = Flags.DYNAMIC_CONFIG_SERVER_PROVISIONING.bindTo(flagSource);
    }

    /**
     * Ensure sufficient nodes are reserved or active for the given application, group and cluster
     *
     * @param application        the application we are allocating to
     * @param cluster            the cluster and group we are allocating to
     * @param requestedNodes     a specification of the requested nodes
     * @param surplusActiveNodes currently active nodes which are available to be assigned to this group.
     *                           This method will remove from this list if it finds it needs additional nodes
     * @param indices            the next available node indices for this cluster.
     *                           This method will consume these when it allocates new nodes to the cluster.
     * @return the list of nodes this cluster group will have allocated if activated
     */
    // Note: This operation may make persisted changes to the set of reserved and inactive nodes,
    // but it may not change the set of active nodes, as the active nodes must stay in sync with the
    // active config model which is changed on activate
    public List<Node> prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                              List<Node> surplusActiveNodes, NodeIndices indices, int wantedGroups) {

        String allocateOsRequirement = allocateOsRequirementFlag
                .with(FetchVector.Dimension.APPLICATION_ID, application.serializedForm())
                .value();

        // Try preparing in memory without global unallocated lock. Most of the time there should be no changes and we
        // can return nodes previously allocated.
        {
            NodeAllocation probeAllocation = prepareAllocation(application, cluster, requestedNodes, surplusActiveNodes,
                                                               indices::probeNext, wantedGroups, PROBE_LOCK,
                                                               allocateOsRequirement);
            if (probeAllocation.fulfilledAndNoChanges()) {
                List<Node> acceptedNodes = probeAllocation.finalNodes();
                surplusActiveNodes.removeAll(acceptedNodes);
                indices.commitProbe();
                return acceptedNodes;
            }
            indices.resetProbe();
        }

        // There were some changes, so re-do the allocation with locks
        try (Mutex lock = nodeRepository.nodes().lock(application);
             Mutex allocationLock = nodeRepository.nodes().lockUnallocated()) {

            NodeAllocation allocation = prepareAllocation(application, cluster, requestedNodes, surplusActiveNodes,
                                                          indices::next, wantedGroups, allocationLock,
                                                          allocateOsRequirement);
            NodeType hostType = allocation.nodeType().hostType();
            boolean hostTypeSupportsDynamicProvisioning = hostType == NodeType.host ||
                                                      (hostType.isConfigServerHostLike() &&
                                                       provisionConfigServerDynamically.value());
            if (nodeRepository.zone().getCloud().dynamicProvisioning() && hostTypeSupportsDynamicProvisioning) {
                final Version osVersion;
                if (allocateOsRequirement.equals("rhel8")) {
                    osVersion = new Version(8, Integer.MAX_VALUE /* always use latest 8 version */, 0);
                } else {
                    osVersion = nodeRepository.osVersions().targetFor(hostType).orElse(Version.emptyVersion);
                }
                HostSharing sharing = hostSharing(requestedNodes, hostType);
                List<ProvisionedHost> provisionedHosts = allocation.hostDeficit()
                        .map(deficit -> {
                            return hostProvisioner.get().provisionHosts(allocation.provisionIndices(deficit.count()),
                                                                        hostType,
                                                                        deficit.resources(),
                                                                        application,
                                                                        osVersion,
                                                                        sharing);
                        })
                        .orElseGet(List::of);

                // At this point we have started provisioning of the hosts, the first priority is to make sure that
                // the returned hosts are added to the node-repo so that they are tracked by the provision maintainers
                List<Node> hosts = provisionedHosts.stream()
                                                   .map(ProvisionedHost::generateHost)
                                                   .collect(Collectors.toList());
                nodeRepository.nodes().addNodes(hosts, Agent.application);

                // Offer the nodes on the newly provisioned hosts, this should be enough to cover the deficit
                List<NodeCandidate> candidates = provisionedHosts.stream()
                                                                 .map(host -> NodeCandidate.createNewExclusiveChild(host.generateNode(),
                                                                                                                    host.generateHost()))
                                                                 .collect(Collectors.toList());
                allocation.offer(candidates);
            }

            if (! allocation.fulfilled() && requestedNodes.canFail())
                throw new OutOfCapacityException((cluster.group().isPresent() ? "Out of capacity on " + cluster.group().get() :"") +
                                                 allocation.outOfCapacityDetails());

            // Carry out and return allocation
            nodeRepository.nodes().reserve(allocation.reservableNodes());
            nodeRepository.nodes().addReservedNodes(new LockedNodeList(allocation.newNodes(), allocationLock));
            List<Node> acceptedNodes = allocation.finalNodes();
            surplusActiveNodes.removeAll(acceptedNodes);
            return acceptedNodes;
        }
    }

    private NodeAllocation prepareAllocation(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                                             List<Node> surplusActiveNodes, Supplier<Integer> nextIndex, int wantedGroups,
                                             Mutex allocationLock, String allocateOsRequirement) {
        LockedNodeList allNodes = nodeRepository.nodes().list(allocationLock);
        NodeAllocation allocation = new NodeAllocation(allNodes, application, cluster, requestedNodes,
                nextIndex, nodeRepository);
        NodePrioritizer prioritizer = new NodePrioritizer(
                allNodes, application, cluster, requestedNodes, wantedGroups,
                nodeRepository.zone().getCloud().dynamicProvisioning(), nodeRepository.nameResolver(),
                nodeRepository.resourcesCalculator(), nodeRepository.spareCount(), allocateOsRequirement);
        allocation.offer(prioritizer.collect(surplusActiveNodes));
        return allocation;
    }

    private static HostSharing hostSharing(NodeSpec spec, NodeType hostType) {
        HostSharing sharing = spec.isExclusive() ? HostSharing.exclusive : HostSharing.any;
        if (!hostType.isSharable() && sharing != HostSharing.any) {
            throw new IllegalArgumentException(hostType + " does not support sharing requirement");
        }
        return sharing;
    }

}
