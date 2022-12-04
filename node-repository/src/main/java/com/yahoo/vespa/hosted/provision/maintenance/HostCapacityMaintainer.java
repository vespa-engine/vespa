// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.lang.MutableInteger;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.JacksonFlag;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.ClusterCapacity;
import com.yahoo.vespa.flags.custom.SharedHost;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.vespa.hosted.provision.provisioning.NodeCandidate;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;
import com.yahoo.vespa.hosted.provision.provisioning.NodeSpec;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 * @author mpolden
 */
public class HostCapacityMaintainer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(HostCapacityMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;
    private final ListFlag<ClusterCapacity> preprovisionCapacityFlag;
    private final JacksonFlag<SharedHost> sharedHostFlag;

    HostCapacityMaintainer(NodeRepository nodeRepository,
                           Duration interval,
                           HostProvisioner hostProvisioner,
                           FlagSource flagSource,
                           Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
        this.preprovisionCapacityFlag = PermanentFlags.PREPROVISION_CAPACITY.bindTo(flagSource);
        this.sharedHostFlag = PermanentFlags.SHARED_HOST.bindTo(flagSource);
    }

    @Override
    protected double maintain() {
        NodeList nodes = nodeRepository().nodes().list();
        List<Node> excessHosts;
        try {
            excessHosts = provision(nodes);
        } catch (NodeAllocationException | IllegalStateException e) {
            log.log(Level.WARNING, "Failed to allocate preprovisioned capacity and/or find excess hosts: " + e.getMessage());
            return 0;  // avoid removing excess hosts
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Failed to allocate preprovisioned capacity and/or find excess hosts", e);
            return 0;  // avoid removing excess hosts
        }

        return markForRemoval(excessHosts);
    }

    private double markForRemoval(List<Node> excessHosts) {
        if (excessHosts.isEmpty()) return 1;

        int attempts = 0, success = 0;
        for (List<Node> typeExcessHosts : excessHosts.stream().collect(Collectors.groupingBy(Node::type)).values()) {
            attempts++;
            // All nodes in the list are hosts of the same type, so they use the same lock regardless of their allocation
            Optional<NodeMutex> appMutex = nodeRepository().nodes().lockAndGet(typeExcessHosts.get(0), Duration.ofSeconds(10));
            if (appMutex.isEmpty()) continue;
            try (Mutex lock = appMutex.get();
                 Mutex unallocatedLock = nodeRepository().nodes().lockUnallocated()) {
                // Re-read all nodes under lock and compute the candidates for removal. The actual nodes we want
                // to mark for removal is the intersection with typeExcessHosts
                List<Node> toMarkForRemoval = candidatesForRemoval(nodeRepository().nodes().list().asList()).stream()
                        .filter(typeExcessHosts::contains)
                        .toList();

                for (Node host : toMarkForRemoval) {
                    attempts++;
                    // Retire the host to parked if possible, otherwise move it straight to parked
                    if (EnumSet.of(Node.State.reserved, Node.State.active, Node.State.inactive).contains(host.state())) {
                        Node retiredHost = host.withWantToRetire(true, true, Agent.HostCapacityMaintainer, nodeRepository().clock().instant());
                        nodeRepository().nodes().write(retiredHost, lock);
                    } else nodeRepository().nodes().park(host.hostname(), true, Agent.HostCapacityMaintainer, "Parked for removal");
                    success++;
                }
            } catch (UncheckedTimeoutException e) {
                log.log(Level.WARNING, "Failed to mark excess hosts for deprovisioning: Failed to get lock, will retry later");
            }
            success++;
        }
        return asSuccessFactor(attempts, attempts - success);
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
        var nodes = new ArrayList<>(provisionUntilNoDeficit(nodeList));
        var sharedHosts = new HashMap<>(findSharedHosts(nodeList));
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

    private static List<Node> candidatesForRemoval(List<Node> nodes) {
        Map<String, Node> removableHostsByHostname = new HashMap<>();
        for (var node : nodes) {
            if (canRemoveHost(node)) {
                removableHostsByHostname.put(node.hostname(), node);
            }
        }
        for (var node : nodes) {
            if (node.parentHostname().isPresent() && !canDeprovision(node)) {
                removableHostsByHostname.remove(node.parentHostname().get());
            }
        }
        return List.copyOf(removableHostsByHostname.values());
    }

    private static boolean canRemoveHost(Node host) {
        return switch (host.type()) {
            // TODO: Mark empty tenant hosts as wanttoretire & wanttodeprovision elsewhere, then handle as confighost here
            case host -> host.state() != Node.State.parked || host.status().wantToDeprovision();
            case confighost, controllerhost -> canDeprovision(host);
            default -> false;
        };
    }

    static boolean canDeprovision(Node node) {
        return node.status().wantToDeprovision() && (node.state() == Node.State.parked ||
                                                     node.state() == Node.State.failed);
    }

    private Map<String, Node> findSharedHosts(NodeList nodeList) {
        return nodeList.stream()
                .filter(node -> nodeRepository().nodes().canAllocateTenantNodeTo(node, true))
                .filter(node -> node.reservedTo().isEmpty())
                .filter(node -> node.exclusiveToApplicationId().isEmpty())
                .collect(Collectors.toMap(Node::hostname, Function.identity()));
    }

    /**
     * @return the nodes in {@code nodeList} plus all hosts provisioned, plus all preprovision capacity
     *         nodes that were allocated.
     * @throws NodeAllocationException if there were problems provisioning hosts, and in case message
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
            List<Node> hosts = new ArrayList<>();
            hostProvisioner.provisionHosts(provisionIndices, NodeType.host, nodeResources, ApplicationId.defaultId(),
                                           osVersion, HostSharing.shared, Optional.empty(), nodeRepository().zone().cloud().account(),
                                           provisionedHosts -> {
                                               hosts.addAll(provisionedHosts.stream().map(ProvisionedHost::generateHost).toList());
                                               nodeRepository().nodes().addNodes(hosts, Agent.HostCapacityMaintainer);
                                           });
            return hosts;
        } catch (NodeAllocationException | IllegalArgumentException | IllegalStateException e) {
            throw new NodeAllocationException("Failed to provision " + count + " " + nodeResources + ": " + e.getMessage(),
                                              ! (e instanceof NodeAllocationException nae) || nae.retryable());
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
            NodesAndHosts<LockedNodeList> nodesAndHosts = NodesAndHosts.create(new LockedNodeList(mutableNodes, () -> {}));
            List<Node> candidates = findCandidates(clusterCapacity, clusterIndex, nodesAndHosts);
            int deficit = Math.max(0, clusterCapacity.count() - candidates.size());
            if (deficit > 0) {
                return Optional.of(clusterCapacity.withCount(deficit));
            }

            // Simulate allocating the cluster
            mutableNodes.addAll(candidates);
        }

        return Optional.empty();
    }

    private List<Node> findCandidates(ClusterCapacity clusterCapacity, int clusterIndex, NodesAndHosts<LockedNodeList> nodesAndHosts) {
        NodeResources nodeResources = toNodeResources(clusterCapacity);

        // We'll allocate each ClusterCapacity as a unique cluster in a dummy application
        ApplicationId applicationId = ApplicationId.defaultId();
        ClusterSpec.Id clusterId = ClusterSpec.Id.from(String.valueOf(clusterIndex));
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, clusterId)
                // build() requires a version, even though it is not (should not be) used
                .vespaVersion(Vtag.currentVersion)
                .build();
        NodeSpec nodeSpec = NodeSpec.from(clusterCapacity.count(), nodeResources, false, true, nodeRepository().zone().cloud().account());
        int wantedGroups = 1;

        NodePrioritizer prioritizer = new NodePrioritizer(nodesAndHosts, applicationId, clusterSpec, nodeSpec, wantedGroups,
                true, nodeRepository().nameResolver(), nodeRepository().nodes(), nodeRepository().resourcesCalculator(),
                nodeRepository().spareCount());
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
}
