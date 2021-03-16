// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.JacksonFlag;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Nodes;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.vespa.hosted.provision.provisioning.NodeCandidate;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;
import com.yahoo.vespa.hosted.provision.provisioning.NodeSpec;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.yolean.Exceptions;

import javax.naming.NameNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 * @author mpolden
 */
public class DynamicProvisioningMaintainer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(DynamicProvisioningMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;
    private final ListFlag<ClusterCapacity> preprovisionCapacityFlag;
    private final JacksonFlag<SharedHost> sharedHostFlag;
    private final StringFlag allocateOsRequirement;

    DynamicProvisioningMaintainer(NodeRepository nodeRepository,
                                  Duration interval,
                                  HostProvisioner hostProvisioner,
                                  FlagSource flagSource,
                                  Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
        this.preprovisionCapacityFlag = PermanentFlags.PREPROVISION_CAPACITY.bindTo(flagSource);
        this.sharedHostFlag = PermanentFlags.SHARED_HOST.bindTo(flagSource);
        this.allocateOsRequirement = Flags.ALLOCATE_OS_REQUIREMENT.bindTo(flagSource);
    }

    @Override
    protected boolean maintain() {
        try (Mutex lock = nodeRepository().nodes().lockUnallocated()) {
            NodeList nodes = nodeRepository().nodes().list();
            resumeProvisioning(nodes, lock);
            convergeToCapacity(nodes);
        }
        return true;
    }

    /** Resume provisioning of already provisioned hosts and their children */
    private void resumeProvisioning(NodeList nodes, Mutex lock) {
        Map<String, Set<Node>> nodesByProvisionedParentHostname =
                nodes.nodeType(NodeType.tenant, NodeType.config, NodeType.controller)
                     .asList()
                     .stream()
                     .filter(node -> node.parentHostname().isPresent())
                     .collect(Collectors.groupingBy(node -> node.parentHostname().get(), Collectors.toSet()));

        nodes.state(Node.State.provisioned).nodeType(NodeType.host, NodeType.confighost, NodeType.controllerhost).forEach(host -> {
            Set<Node> children = nodesByProvisionedParentHostname.getOrDefault(host.hostname(), Set.of());
            try {
                List<Node> updatedNodes = hostProvisioner.provision(host, children);
                verifyDns(updatedNodes);
                nodeRepository().nodes().write(updatedNodes, lock);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(Level.INFO, "Failed to provision " + host.hostname() + " with " + children.size() + " children: " +
                                    Exceptions.toMessageString(e));
            } catch (FatalProvisioningException e) {
                log.log(Level.SEVERE, "Failed to provision " + host.hostname() + " with " + children.size()  +
                                      " children, failing out the host recursively", e);
                // Fail out as operator to force a quick redeployment
                nodeRepository().nodes().failRecursively(
                        host.hostname(), Agent.operator, "Failed by HostProvisioner due to provisioning failure");
            } catch (RuntimeException e) {
                if (e.getCause() instanceof NameNotFoundException)
                    log.log(Level.INFO, "Failed to provision " + host.hostname() + ", will retry in " + interval() + ": " + e.getMessage());
                else
                    log.log(Level.WARNING, "Failed to provision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }

    /** Converge zone to wanted capacity */
    private void convergeToCapacity(NodeList nodes) {
        List<Node> excessHosts;
        try {
            excessHosts = provision(nodes);
        } catch (OutOfCapacityException | IllegalStateException e) {
            log.log(Level.WARNING, "Failed to provision preprovision capacity and/or find excess hosts: " + e.getMessage());
            return;  // avoid removing excess hosts
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to provision preprovision capacity and/or find excess hosts", e);
            return;  // avoid removing excess hosts
        }

        excessHosts.forEach(host -> {
            try {
                hostProvisioner.deprovision(host);
                nodeRepository().nodes().removeRecursively(host, true);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to deprovision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }

    /**
     * Provision hosts to ensure there is room to allocate spare nodes.
     *
     * @param nodeList list of all nodes
     * @return excess hosts that can safely be deprovisioned: An excess host 1. contains no nodes allocated
     *         to an application, and assuming the spare nodes have been allocated, and 2. is not parked
     *         without wantToDeprovision (which means an operator is looking at the node).
     */
    private List<Node> provision(NodeList nodeList) {
        final List<Node> nodes = new ArrayList<>(provisionUntilNoDeficit(nodeList));


        Map<String, Node> sharedHosts = new HashMap<>(findSharedHosts(nodeList));

        int minCount = sharedHostFlag.value().getMinCount();
        int deficit = minCount - sharedHosts.size();
        if (deficit > 0) {
            provisionHosts(deficit, NodeResources.unspecified())
                    .forEach(host -> {
                        sharedHosts.put(host.hostname(), host);
                        nodes.add(host);
                    });
        }

        return candidatesForRemoval(nodes).stream()
                .sorted(Comparator.comparing(node -> node.history().events().stream()
                                                         .map(History.Event::at).min(Comparator.naturalOrder()).orElse(Instant.MIN)))
                .filter(node -> {
                    if (!sharedHosts.containsKey(node.hostname()) || sharedHosts.size() > minCount) {
                        sharedHosts.remove(node.hostname());
                        return true;
                    } else {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<Node> candidatesForRemoval(List<Node> nodes) {
        Map<String, Node> hostsByHostname = new HashMap<>(nodes.stream()
                .filter(node -> {
                    switch (node.type()) {
                        case host:
                            // TODO: Mark empty tenant hosts as wanttoretire & wanttodeprovision elsewhere, then handle as confighost here
                            return node.state() != Node.State.parked || node.status().wantToDeprovision();
                        case confighost:
                        case controllerhost:
                            return node.state() == Node.State.parked && node.status().wantToDeprovision();
                        default:
                            return false;
                    }
                })
                .collect(Collectors.toMap(Node::hostname, Function.identity())));

        nodes.stream()
             .filter(node -> node.allocation().isPresent())
             .flatMap(node -> node.parentHostname().stream())
             .distinct()
             .forEach(hostsByHostname::remove);

        return List.copyOf(hostsByHostname.values());
    }

    private Map<String, Node> findSharedHosts(NodeList nodeList) {
        return nodeList.stream()
                .filter(node -> Nodes.canAllocateTenantNodeTo(node, true))
                .filter(node -> node.reservedTo().isEmpty())
                .filter(node -> node.exclusiveTo().isEmpty())
                .collect(Collectors.toMap(Node::hostname, Function.identity()));
    }

    /**
     * @return the nodes in {@code nodeList} plus all hosts provisioned, plus all preprovision capacity
     *         nodes that were allocated.
     * @throws OutOfCapacityException if there were problems provisioning hosts, and in case message
     *         should be sufficient (avoid no stack trace)
     * @throws IllegalStateException if there was an algorithmic problem, and in case message
     *         should be sufficient (avoid no stack trace).
     */
    private List<Node> provisionUntilNoDeficit(NodeList nodeList) {
        List<ClusterCapacity> preprovisionCapacity = preprovisionCapacityFlag.value();

        // Worst-case each ClusterCapacity in preprovisionCapacity will require an allocation.
        int maxProvisions = preprovisionCapacity.size();

        var nodesPlusProvisioned = new ArrayList<>(nodeList.asList());
        for (int numProvisions = 0;; ++numProvisions) {
            var nodesPlusProvisionedPlusAllocated = new ArrayList<>(nodesPlusProvisioned);
            Optional<ClusterCapacity> deficit = allocatePreprovisionCapacity(preprovisionCapacity, nodesPlusProvisionedPlusAllocated);
            if (deficit.isEmpty()) {
                return nodesPlusProvisionedPlusAllocated;
            }

            if (numProvisions >= maxProvisions) {
                throw new IllegalStateException("Have provisioned " + numProvisions + " times but there's still deficit: aborting");
            }

            nodesPlusProvisioned.addAll(provisionHosts(deficit.get().count(), toNodeResources(deficit.get())));
        }
    }

    private List<Node> provisionHosts(int count, NodeResources nodeResources) {
        try {
            Version osVersion = nodeRepository().osVersions().targetFor(NodeType.host).orElse(Version.emptyVersion);
            List<Integer> provisionIndices = nodeRepository().database().readProvisionIndices(count);
            List<Node> hosts = hostProvisioner.provisionHosts(provisionIndices, NodeType.host, nodeResources,
                    ApplicationId.defaultId(), osVersion, HostSharing.shared)
                    .stream()
                    .map(ProvisionedHost::generateHost)
                    .collect(Collectors.toList());
            nodeRepository().nodes().addNodes(hosts, Agent.DynamicProvisioningMaintainer);
            return hosts;
        } catch (OutOfCapacityException | IllegalArgumentException | IllegalStateException e) {
            throw new OutOfCapacityException("Failed to provision " + count + " " + nodeResources + ": " + e.getMessage());
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to provision " + count + " " + nodeResources + ", will retry in " + interval(), e);
        }
    }

    /**
     * Try to allocate the preprovision cluster capacity.
     *
     * @param mutableNodes represents all nodes in the node repo.  As preprovision capacity is virtually allocated
     *                     they are added to {@code mutableNodes}
     * @return the part of a cluster capacity it was unable to allocate, if any
     */
    private Optional<ClusterCapacity> allocatePreprovisionCapacity(List<ClusterCapacity> preprovisionCapacity,
                                                                   ArrayList<Node> mutableNodes) {
        for (int clusterIndex = 0; clusterIndex < preprovisionCapacity.size(); ++clusterIndex) {
            ClusterCapacity clusterCapacity = preprovisionCapacity.get(clusterIndex);
            LockedNodeList nodeList = new LockedNodeList(mutableNodes, () -> {});
            List<Node> candidates = findCandidates(clusterCapacity, clusterIndex, nodeList);
            int deficit = Math.max(0, clusterCapacity.count() - candidates.size());
            if (deficit > 0) {
                return Optional.of(clusterCapacity.withCount(deficit));
            }

            // Simulate allocating the cluster
            mutableNodes.addAll(candidates);
        }

        return Optional.empty();
    }

    private List<Node> findCandidates(ClusterCapacity clusterCapacity, int clusterIndex, LockedNodeList nodeList) {
        NodeResources nodeResources = toNodeResources(clusterCapacity);

        // We'll allocate each ClusterCapacity as a unique cluster in a dummy application
        ApplicationId applicationId = ApplicationId.defaultId();
        ClusterSpec.Id clusterId = ClusterSpec.Id.from(String.valueOf(clusterIndex));
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, clusterId)
                // build() requires a version, even though it is not (should not be) used
                .vespaVersion(Vtag.currentVersion)
                .build();
        NodeSpec nodeSpec = NodeSpec.from(clusterCapacity.count(), nodeResources, false, true);
        int wantedGroups = 1;

        String allocateOsRequirement = this.allocateOsRequirement
                .with(FetchVector.Dimension.APPLICATION_ID, ApplicationId.defaultId().serializedForm())
                .value();
        NodePrioritizer prioritizer = new NodePrioritizer(nodeList, applicationId, clusterSpec, nodeSpec, wantedGroups,
                true, nodeRepository().nameResolver(), nodeRepository().resourcesCalculator(),
                nodeRepository().spareCount(), allocateOsRequirement);
        List<NodeCandidate> nodeCandidates = prioritizer.collect(List.of());
        MutableInteger index = new MutableInteger(0);
        return nodeCandidates
                .stream()
                .limit(clusterCapacity.count())
                .map(candidate -> candidate.toNode()
                        .allocate(applicationId,
                                  ClusterMembership.from(clusterSpec, index.next()),
                                  nodeResources,
                                  nodeRepository().clock().instant()))
                .collect(Collectors.toList());

    }

    private static NodeResources toNodeResources(ClusterCapacity clusterCapacity) {
        return new NodeResources(clusterCapacity.vcpu(), clusterCapacity.memoryGb(), clusterCapacity.diskGb(),
                clusterCapacity.bandwidthGbps());
    }

    /** Verify DNS configuration of given nodes */
    private void verifyDns(List<Node> nodes) {
        for (var node : nodes) {
            for (var ipAddress : node.ipConfig().primary()) {
                IP.verifyDns(node.hostname(), ipAddress, nodeRepository().nameResolver());
            }
        }
    }
}
